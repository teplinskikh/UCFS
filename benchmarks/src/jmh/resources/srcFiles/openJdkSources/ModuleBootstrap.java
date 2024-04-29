/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.module;

import java.io.File;
import java.io.PrintStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangModuleAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.loader.BootLoader;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.misc.CDS;
import jdk.internal.perf.PerfCounter;

/**
 * Initializes/boots the module system.
 *
 * The {@link #boot() boot} method is called early in the startup to initialize
 * the module system. In summary, the boot method creates a Configuration by
 * resolving a set of module names specified via the launcher (or equivalent)
 * -m and --add-modules options. The modules are located on a module path that
 * is constructed from the upgrade module path, system modules, and application
 * module path. The Configuration is instantiated as the boot layer with each
 * module in the configuration defined to a class loader.
 */

public final class ModuleBootstrap {
    private ModuleBootstrap() { }

    private static final String JAVA_BASE = "java.base";

    private static final String ALL_DEFAULT = "ALL-DEFAULT";

    private static final String ALL_UNNAMED = "ALL-UNNAMED";

    private static final String ALL_SYSTEM = "ALL-SYSTEM";

    private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaLangModuleAccess JLMA = SharedSecrets.getJavaLangModuleAccess();

    private static final ModulePatcher patcher = initModulePatcher();

    /**
     * Returns the ModulePatcher for the initial configuration.
     */
    public static ModulePatcher patcher() {
        return patcher;
    }

    private static volatile ModuleFinder unlimitedFinder;
    private static volatile ModuleFinder limitedFinder;

    /**
     * Returns the ModuleFinder for the initial configuration before
     * observability is limited by the --limit-modules command line option.
     *
     * @apiNote Used to support locating modules {@code java.instrument} and
     * {@code jdk.management.agent} modules when they are loaded dynamically.
     */
    public static ModuleFinder unlimitedFinder() {
        ModuleFinder finder = unlimitedFinder;
        if (finder == null) {
            return ModuleFinder.ofSystem();
        } else {
            return finder;
        }
    }

    /**
     * Returns the ModuleFinder for the initial configuration.
     *
     * @apiNote Used to support "{@code java --list-modules}".
     */
    public static ModuleFinder limitedFinder() {
        ModuleFinder finder = limitedFinder;
        if (finder == null) {
            return unlimitedFinder();
        } else {
            return finder;
        }
    }

    /**
     * Returns true if the archived boot layer can be used. The system properties
     * are checked in the order that they are used by boot2.
     */
    private static boolean canUseArchivedBootLayer() {
        return getProperty("jdk.module.upgrade.path") == null &&
               getProperty("jdk.module.path") == null &&
               getProperty("jdk.module.patch.0") == null &&       
               getProperty("jdk.module.addmods.0") == null  &&    
               getProperty("jdk.module.limitmods") == null &&     
               getProperty("jdk.module.addreads.0") == null &&    
               getProperty("jdk.module.addexports.0") == null &&  
               getProperty("jdk.module.addopens.0") == null;      
    }

    /**
     * Initialize the module system, returning the boot layer. The boot layer
     * is obtained from the CDS archive if possible, otherwise it is generated
     * from the module graph.
     *
     * @see java.lang.System#initPhase2(boolean, boolean)
     */
    public static ModuleLayer boot() {
        Counters.start();

        ModuleLayer bootLayer;
        ArchivedBootLayer archivedBootLayer = ArchivedBootLayer.get();
        if (archivedBootLayer != null) {
            assert canUseArchivedBootLayer();
            bootLayer = archivedBootLayer.bootLayer();
            BootLoader.getUnnamedModule(); 
            CDS.defineArchivedModules(ClassLoaders.platformClassLoader(), ClassLoaders.appClassLoader());

            JLA.bindToLoader(bootLayer, ClassLoaders.appClassLoader());
        } else {
            bootLayer = boot2();
        }

        Counters.publish("jdk.module.boot.totalTime");
        return bootLayer;
    }

    private static ModuleLayer boot2() {

        ModuleFinder upgradeModulePath = finderFor("jdk.module.upgrade.path");
        ModuleFinder appModulePath = finderFor("jdk.module.path");
        boolean isPatched = patcher.hasPatches();
        String mainModule = System.getProperty("jdk.module.main");
        Set<String> addModules = addModules();
        Set<String> limitModules = limitModules();

        PrintStream traceOutput = null;
        String trace = getAndRemoveProperty("jdk.module.showModuleResolution");
        if (trace != null && Boolean.parseBoolean(trace))
            traceOutput = System.out;

        Counters.add("jdk.module.boot.0.commandLineTime");


        SystemModules systemModules = null;
        ModuleFinder systemModuleFinder;

        boolean haveModulePath = (appModulePath != null || upgradeModulePath != null);
        boolean needResolution = true;
        boolean canArchive = false;
        boolean hasSplitPackages;
        boolean hasIncubatorModules;

        ArchivedModuleGraph archivedModuleGraph = ArchivedModuleGraph.get(mainModule);
        if (archivedModuleGraph != null
                && !haveModulePath
                && addModules.isEmpty()
                && limitModules.isEmpty()
                && !isPatched) {
            systemModuleFinder = archivedModuleGraph.finder();
            hasSplitPackages = archivedModuleGraph.hasSplitPackages();
            hasIncubatorModules = archivedModuleGraph.hasIncubatorModules();
            needResolution = (traceOutput != null);
        } else {
            if (!haveModulePath && addModules.isEmpty() && limitModules.isEmpty()) {
                systemModules = SystemModuleFinders.systemModules(mainModule);
                if (systemModules != null && !isPatched) {
                    needResolution = (traceOutput != null);
                    if (CDS.isDumpingStaticArchive())
                        canArchive = true;
                }
            }
            if (systemModules == null) {
                systemModules = SystemModuleFinders.allSystemModules();
            }
            if (systemModules != null) {
                systemModuleFinder = SystemModuleFinders.of(systemModules);
            } else {
                systemModules = new ExplodedSystemModules();
                systemModuleFinder = SystemModuleFinders.ofSystem();
            }

            hasSplitPackages = systemModules.hasSplitPackages();
            hasIncubatorModules = systemModules.hasIncubatorModules();
            archivedModuleGraph = null;
        }

        Counters.add("jdk.module.boot.1.systemModulesTime");


        ModuleReference base = systemModuleFinder.find(JAVA_BASE).orElse(null);
        if (base == null)
            throw new InternalError(JAVA_BASE + " not found");
        URI baseUri = base.location().orElse(null);
        if (baseUri == null)
            throw new InternalError(JAVA_BASE + " does not have a location");
        BootLoader.loadModule(base);

        Module baseModule = Modules.defineModule(null, base.descriptor(), baseUri);
        JLA.addEnableNativeAccess(baseModule);


        if (getAndRemoveProperty("jdk.module.validation") != null) {
            int errors = ModulePathValidator.scanAllModules(System.out);
            if (errors > 0) {
                fail("Validation of module path failed");
            }
        }

        Counters.add("jdk.module.boot.2.defineBaseTime");


        ModuleFinder savedModuleFinder = null;
        ModuleFinder finder;
        Set<String> roots;
        if (needResolution) {

            if (upgradeModulePath != null)
                systemModuleFinder = ModuleFinder.compose(upgradeModulePath,
                                                          systemModuleFinder);

            if (appModulePath != null) {
                finder = ModuleFinder.compose(systemModuleFinder, appModulePath);
            } else {
                finder = systemModuleFinder;
            }

            roots = new HashSet<>();

            if (mainModule != null)
                roots.add(mainModule);

            boolean addAllDefaultModules = false;
            boolean addAllSystemModules = false;
            boolean addAllApplicationModules = false;
            for (String mod : addModules) {
                switch (mod) {
                    case ALL_DEFAULT:
                        addAllDefaultModules = true;
                        break;
                    case ALL_SYSTEM:
                        addAllSystemModules = true;
                        break;
                    case ALL_MODULE_PATH:
                        addAllApplicationModules = true;
                        break;
                    default:
                        roots.add(mod);
                }
            }

            savedModuleFinder = finder;
            if (!limitModules.isEmpty()) {
                finder = limitFinder(finder, limitModules, roots);
            }

            if (mainModule == null || addAllDefaultModules) {
                roots.addAll(DefaultRoots.compute(systemModuleFinder, finder));
            }

            if (addAllSystemModules) {
                ModuleFinder f = finder;  
                systemModuleFinder.findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .filter(mn -> f.find(mn).isPresent())  
                    .forEach(mn -> roots.add(mn));
            }

            if (appModulePath != null && addAllApplicationModules) {
                ModuleFinder f = finder;  
                appModulePath.findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .filter(mn -> f.find(mn).isPresent())  
                    .forEach(mn -> roots.add(mn));
            }
        } else {
            finder = systemModuleFinder;
            roots = null;
        }

        Counters.add("jdk.module.boot.3.optionsAndRootsTime");


        Configuration cf;
        if (needResolution) {
            cf = Modules.newBootLayerConfiguration(finder, roots, traceOutput);
        } else {
            if (archivedModuleGraph != null) {
                cf = archivedModuleGraph.configuration();
            } else {
                Map<String, Set<String>> map = systemModules.moduleReads();
                cf = JLMA.newConfiguration(systemModuleFinder, map);
            }
        }

        if (isPatched) {
            patcher.patchedModules()
                    .stream()
                    .filter(mn -> cf.findModule(mn).isEmpty())
                    .forEach(mn -> warnUnknownModule(PATCH_MODULE, mn));
        }

        Counters.add("jdk.module.boot.4.resolveTime");


        Function<String, ClassLoader> clf;
        if (archivedModuleGraph != null) {
            clf = archivedModuleGraph.classLoaderFunction();
        } else {
            clf = ModuleLoaderMap.mappingFunction(cf);
        }

        if (haveModulePath) {
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                String name = mref.descriptor().name();
                ClassLoader cl = clf.apply(name);
                if (cl == null) {
                    if (upgradeModulePath != null
                            && upgradeModulePath.find(name).isPresent())
                        fail(name + ": cannot be loaded from upgrade module path");
                    if (systemModuleFinder.find(name).isEmpty())
                        fail(name + ": cannot be loaded from application module path");
                }
            }
        }

        if (hasSplitPackages || isPatched || haveModulePath) {
            checkSplitPackages(cf, clf);
        }

        loadModules(cf, clf);
        Counters.add("jdk.module.boot.5.loadModulesTime");


        ModuleLayer bootLayer = ModuleLayer.empty().defineModules(cf, clf);
        Counters.add("jdk.module.boot.6.layerCreateTime");


        if (hasIncubatorModules || haveModulePath) {
            checkIncubatingStatus(cf);
        }

        addExtraReads(bootLayer);
        boolean extraExportsOrOpens = addExtraExportsAndOpens(bootLayer);

        addEnableNativeAccess(bootLayer);

        Counters.add("jdk.module.boot.7.adjustModulesTime");

        if (savedModuleFinder != null) {
            unlimitedFinder = new SafeModuleFinder(savedModuleFinder);
            if (savedModuleFinder != finder)
                limitedFinder = new SafeModuleFinder(finder);
        }

        if (CDS.isDumpingStaticArchive() && mainModule != null) {
            String scheme = systemModuleFinder.find(mainModule)
                    .stream()
                    .map(ModuleReference::location)
                    .flatMap(Optional::stream)
                    .findAny()
                    .map(URI::getScheme)
                    .orElse(null);
            if ("jrt".equalsIgnoreCase(scheme)) {
                canArchive = true;
            }
        }

        if (canArchive) {
            ArchivedModuleGraph.archive(hasSplitPackages,
                                        hasIncubatorModules,
                                        systemModuleFinder,
                                        cf,
                                        clf,
                                        mainModule);
            if (!hasSplitPackages && !hasIncubatorModules) {
                ArchivedBootLayer.archive(bootLayer);
            }
        }

        return bootLayer;
    }

    /**
     * Load/register the modules to the built-in class loaders.
     */
    private static void loadModules(Configuration cf,
                                    Function<String, ClassLoader> clf) {
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleReference mref = resolvedModule.reference();
            String name = resolvedModule.name();
            ClassLoader loader = clf.apply(name);
            if (loader == null) {
                if (!name.equals(JAVA_BASE)) {
                    BootLoader.loadModule(mref);
                }
            } else if (loader instanceof BuiltinClassLoader) {
                ((BuiltinClassLoader) loader).loadModule(mref);
            }
        }
    }

    /**
     * Checks for split packages between modules defined to the built-in class
     * loaders.
     */
    private static void checkSplitPackages(Configuration cf,
                                           Function<String, ClassLoader> clf) {
        Map<String, String> packageToModule = new HashMap<>();
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleDescriptor descriptor = resolvedModule.reference().descriptor();
            String name = descriptor.name();
            ClassLoader loader = clf.apply(name);
            if (loader == null || loader instanceof BuiltinClassLoader) {
                for (String p : descriptor.packages()) {
                    String other = packageToModule.putIfAbsent(p, name);
                    if (other != null) {
                        String msg = "Package " + p + " in both module "
                                     + name + " and module " + other;
                        throw new LayerInstantiationException(msg);
                    }
                }
            }
        }
    }

    /**
     * Returns a ModuleFinder that limits observability to the given root
     * modules, their transitive dependences, plus a set of other modules.
     */
    private static ModuleFinder limitFinder(ModuleFinder finder,
                                            Set<String> roots,
                                            Set<String> otherMods)
    {
        Configuration cf = Configuration.empty().resolve(finder,
                                                         ModuleFinder.of(),
                                                         roots);

        Map<String, ModuleReference> map = new HashMap<>();

        cf.modules().stream()
            .map(ResolvedModule::reference)
            .forEach(mref -> map.put(mref.descriptor().name(), mref));

        otherMods.stream()
            .map(finder::find)
            .flatMap(Optional::stream)
            .forEach(mref -> map.putIfAbsent(mref.descriptor().name(), mref));

        Set<ModuleReference> mrefs = new HashSet<>(map.values());

        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(map.get(name));
            }
            @Override
            public Set<ModuleReference> findAll() {
                return mrefs;
            }
        };
    }

    /**
     * Creates a finder from the module path that is the value of the given
     * system property and optionally patched by --patch-module
     */
    private static ModuleFinder finderFor(String prop) {
        String s = System.getProperty(prop);
        if (s == null) {
            return null;
        } else {
            String[] dirs = s.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir: dirs) {
                paths[i++] = Path.of(dir);
            }
            return ModulePath.of(patcher, paths);
        }
    }

    /**
     * Initialize the module patcher for the initial configuration passed on the
     * value of the --patch-module options.
     */
    private static ModulePatcher initModulePatcher() {
        Map<String, List<String>> map = decode("jdk.module.patch.",
                                               File.pathSeparator,
                                               false);
        return new ModulePatcher(map);
    }

    /**
     * Returns the set of module names specified by --add-module options.
     */
    private static Set<String> addModules() {
        String prefix = "jdk.module.addmods.";
        int index = 0;
        String value = getAndRemoveProperty(prefix + index);
        if (value == null) {
            return Set.of();
        } else {
            Set<String> modules = new HashSet<>();
            while (value != null) {
                for (String s : value.split(",")) {
                    if (!s.isEmpty())
                        modules.add(s);
                }
                index++;
                value = getAndRemoveProperty(prefix + index);
            }
            return modules;
        }
    }

    /**
     * Returns the set of module names specified by --limit-modules.
     */
    private static Set<String> limitModules() {
        String value = getAndRemoveProperty("jdk.module.limitmods");
        if (value == null) {
            return Set.of();
        } else {
            Set<String> names = new HashSet<>();
            for (String name : value.split(",")) {
                if (name.length() > 0) names.add(name);
            }
            return names;
        }
    }

    /**
     * Process the --add-reads options to add any additional read edges that
     * are specified on the command-line.
     */
    private static void addExtraReads(ModuleLayer bootLayer) {

        Map<String, List<String>> map = decode("jdk.module.addreads.");
        if (map.isEmpty())
            return;

        for (Map.Entry<String, List<String>> e : map.entrySet()) {

            String mn = e.getKey();
            Optional<Module> om = bootLayer.findModule(mn);
            if (om.isEmpty()) {
                warnUnknownModule(ADD_READS, mn);
                continue;
            }
            Module m = om.get();

            for (String name : e.getValue()) {
                if (ALL_UNNAMED.equals(name)) {
                    Modules.addReadsAllUnnamed(m);
                } else {
                    om = bootLayer.findModule(name);
                    if (om.isPresent()) {
                        Modules.addReads(m, om.get());
                    } else {
                        warnUnknownModule(ADD_READS, name);
                    }
                }
            }
        }
    }

    /**
     * Process the --add-exports and --add-opens options to export/open
     * additional packages specified on the command-line.
     */
    private static boolean addExtraExportsAndOpens(ModuleLayer bootLayer) {
        boolean extraExportsOrOpens = false;

        String prefix = "jdk.module.addexports.";
        Map<String, List<String>> extraExports = decode(prefix);
        if (!extraExports.isEmpty()) {
            addExtraExportsOrOpens(bootLayer, extraExports, false);
            extraExportsOrOpens = true;
        }


        prefix = "jdk.module.addopens.";
        Map<String, List<String>> extraOpens = decode(prefix);
        if (!extraOpens.isEmpty()) {
            addExtraExportsOrOpens(bootLayer, extraOpens, true);
            extraExportsOrOpens = true;
        }

        return extraExportsOrOpens;
    }

    private static void addExtraExportsOrOpens(ModuleLayer bootLayer,
                                               Map<String, List<String>> map,
                                               boolean opens)
    {
        String option = opens ? ADD_OPENS : ADD_EXPORTS;
        for (Map.Entry<String, List<String>> e : map.entrySet()) {

            String key = e.getKey();
            String[] s = key.split("/");
            if (s.length != 2)
                fail(unableToParse(option, "<module>/<package>", key));

            String mn = s[0];
            String pn = s[1];
            if (mn.isEmpty() || pn.isEmpty())
                fail(unableToParse(option, "<module>/<package>", key));

            Module m;
            Optional<Module> om = bootLayer.findModule(mn);
            if (om.isEmpty()) {
                warnUnknownModule(option, mn);
                continue;
            }

            m = om.get();

            if (!m.getDescriptor().packages().contains(pn)) {
                warn("package " + pn + " not in " + mn);
                continue;
            }

            for (String name : e.getValue()) {
                boolean allUnnamed = false;
                Module other = null;
                if (ALL_UNNAMED.equals(name)) {
                    allUnnamed = true;
                } else {
                    om = bootLayer.findModule(name);
                    if (om.isPresent()) {
                        other = om.get();
                    } else {
                        warnUnknownModule(option, name);
                        continue;
                    }
                }
                if (allUnnamed) {
                    if (opens) {
                        Modules.addOpensToAllUnnamed(m, pn);
                    } else {
                        Modules.addExportsToAllUnnamed(m, pn);
                    }
                } else {
                    if (opens) {
                        Modules.addOpens(m, pn, other);
                    } else {
                        Modules.addExports(m, pn, other);
                    }
                }
            }
        }
    }

    private static final boolean HAS_ENABLE_NATIVE_ACCESS_FLAG;
    private static final Set<String> USER_NATIVE_ACCESS_MODULES;
    private static final Set<String> JDK_NATIVE_ACCESS_MODULES;

    public static boolean hasEnableNativeAccessFlag() {
        return HAS_ENABLE_NATIVE_ACCESS_FLAG;
    }

    static {
        USER_NATIVE_ACCESS_MODULES = decodeEnableNativeAccess();
        HAS_ENABLE_NATIVE_ACCESS_FLAG = !USER_NATIVE_ACCESS_MODULES.isEmpty();
        JDK_NATIVE_ACCESS_MODULES = ModuleLoaderMap.nativeAccessModules();
    }

    /**
     * Grants native access to modules selected using the --enable-native-access
     * command line option, and also to JDK modules that need the access.
     */
    private static void addEnableNativeAccess(ModuleLayer layer) {
        addEnableNativeAccess(layer, USER_NATIVE_ACCESS_MODULES, true);
        addEnableNativeAccess(layer, JDK_NATIVE_ACCESS_MODULES, false);
    }

    /**
     * Grants native access for the given modules in the given layer.
     * Warns optionally about modules that were specified, but not present in the layer.
     */
    private static void addEnableNativeAccess(ModuleLayer layer, Set<String> moduleNames, boolean shouldWarn) {
        for (String name : moduleNames) {
            if (name.equals("ALL-UNNAMED")) {
                JLA.addEnableNativeAccessToAllUnnamed();
            } else if (!JLA.addEnableNativeAccess(layer, name) && shouldWarn) {
                warnUnknownModule(ENABLE_NATIVE_ACCESS, name);
            }
        }
    }

    /**
     * Returns the set of module names specified by --enable-native-access options.
     */
    private static Set<String> decodeEnableNativeAccess() {
        String prefix = "jdk.module.enable.native.access.";
        int index = 0;
        String value = getAndRemoveProperty(prefix + index);
        Set<String> modules = new HashSet<>();
        if (value == null) {
            return modules;
        }
        while (value != null) {
            for (String s : value.split(",")) {
                if (!s.isEmpty())
                    modules.add(s);
            }
            index++;
            value = getAndRemoveProperty(prefix + index);
        }
        return modules;
    }

    /**
     * Decodes the values of --add-reads, -add-exports, --add-opens or
     * --patch-modules options that are encoded in system properties.
     *
     * @param prefix the system property prefix
     * @praam regex the regex for splitting the RHS of the option value
     */
    private static Map<String, List<String>> decode(String prefix,
                                                    String regex,
                                                    boolean allowDuplicates) {
        int index = 0;
        String value = getAndRemoveProperty(prefix + index);
        if (value == null)
            return Map.of();

        Map<String, List<String>> map = new HashMap<>();

        while (value != null) {

            int pos = value.indexOf('=');
            if (pos == -1)
                fail(unableToParse(option(prefix), "<module>=<value>", value));
            if (pos == 0)
                fail(unableToParse(option(prefix), "<module>=<value>", value));

            String key = value.substring(0, pos);

            String rhs = value.substring(pos+1);
            if (rhs.isEmpty())
                fail(unableToParse(option(prefix), "<module>=<value>", value));

            if (!allowDuplicates && map.containsKey(key))
                fail(key + " specified more than once to " + option(prefix));
            List<String> values = map.computeIfAbsent(key, k -> new ArrayList<>());
            int ntargets = 0;
            for (String s : rhs.split(regex)) {
                if (!s.isEmpty()) {
                    values.add(s);
                    ntargets++;
                }
            }
            if (ntargets == 0)
                fail("Target must be specified: " + option(prefix) + " " + value);

            index++;
            value = getAndRemoveProperty(prefix + index);
        }

        return map;
    }

    /**
     * Decodes the values of --add-reads, -add-exports or --add-opens
     * which use the "," to separate the RHS of the option value.
     */
    private static Map<String, List<String>> decode(String prefix) {
        return decode(prefix, ",", true);
    }


    /**
     * Gets the named system property
     */
    private static String getProperty(String key) {
        return System.getProperty(key);
    }

    /**
     * Gets and remove the named system property
     */
    private static String getAndRemoveProperty(String key) {
        return (String) System.getProperties().remove(key);
    }

    /**
     * Checks incubating status of modules in the configuration
     */
    private static void checkIncubatingStatus(Configuration cf) {
        String incubating = null;
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleReference mref = resolvedModule.reference();

            if (ModuleResolution.hasIncubatingWarning(mref)) {
                String mn = mref.descriptor().name();
                if (incubating == null) {
                    incubating = mn;
                } else {
                    incubating += ", " + mn;
                }
            }
        }
        if (incubating != null)
            warn("Using incubator modules: " + incubating);
    }

    /**
     * Throws a RuntimeException with the given message
     */
    static void fail(String m) {
        throw new RuntimeException(m);
    }

    static void warn(String m) {
        System.err.println("WARNING: " + m);
    }

    static void warnUnknownModule(String option, String mn) {
        warn("Unknown module: " + mn + " specified to " + option);
    }

    static String unableToParse(String option, String text, String value) {
        return "Unable to parse " +  option + " " + text + ": " + value;
    }

    private static final String ADD_MODULES  = "--add-modules";
    private static final String ADD_EXPORTS  = "--add-exports";
    private static final String ADD_OPENS    = "--add-opens";
    private static final String ADD_READS    = "--add-reads";
    private static final String PATCH_MODULE = "--patch-module";
    private static final String ENABLE_NATIVE_ACCESS = "--enable-native-access";

    /*
     * Returns the command-line option name corresponds to the specified
     * system property prefix.
     */
    static String option(String prefix) {
        switch (prefix) {
            case "jdk.module.addexports.":
                return ADD_EXPORTS;
            case "jdk.module.addopens.":
                return ADD_OPENS;
            case "jdk.module.addreads.":
                return ADD_READS;
            case "jdk.module.patch.":
                return PATCH_MODULE;
            case "jdk.module.addmods.":
                return ADD_MODULES;
            default:
                throw new IllegalArgumentException(prefix);
        }
    }

    /**
     * Wraps a (potentially not thread safe) ModuleFinder created during startup
     * for use after startup.
     */
    static class SafeModuleFinder implements ModuleFinder {
        private final Set<ModuleReference> mrefs;
        private volatile Map<String, ModuleReference> nameToModule;

        SafeModuleFinder(ModuleFinder finder) {
            this.mrefs = Collections.unmodifiableSet(finder.findAll());
        }
        @Override
        public Optional<ModuleReference> find(String name) {
            Objects.requireNonNull(name);
            Map<String, ModuleReference> nameToModule = this.nameToModule;
            if (nameToModule == null) {
                this.nameToModule = nameToModule = mrefs.stream()
                        .collect(Collectors.toMap(m -> m.descriptor().name(),
                                                  Function.identity()));
            }
            return Optional.ofNullable(nameToModule.get(name));
        }
        @Override
        public Set<ModuleReference> findAll() {
            return mrefs;
        }
    }

    /**
     * Counters for startup performance analysis.
     */
    static class Counters {
        private static final boolean PUBLISH_COUNTERS;
        private static final boolean PRINT_COUNTERS;
        private static Map<String, Long> counters;
        private static long startTime;
        private static long previousTime;

        static {
            String s = System.getProperty("jdk.module.boot.usePerfData");
            if (s == null) {
                PUBLISH_COUNTERS = false;
                PRINT_COUNTERS = false;
            } else {
                PUBLISH_COUNTERS = true;
                PRINT_COUNTERS = s.equals("debug");
                counters = new LinkedHashMap<>();  
            }
        }

        /**
         * Start counting time.
         */
        static void start() {
            if (PUBLISH_COUNTERS) {
                startTime = previousTime = System.nanoTime();
            }
        }

        /**
         * Add a counter - storing the time difference between now and the
         * previous add or the start.
         */
        static void add(String name) {
            if (PUBLISH_COUNTERS) {
                long current = System.nanoTime();
                long elapsed = current - previousTime;
                previousTime = current;
                counters.put(name, elapsed);
            }
        }

        /**
         * Publish the counters to the instrumentation buffer or stdout.
         */
        static void publish(String totalTimeName) {
            if (PUBLISH_COUNTERS) {
                long currentTime = System.nanoTime();
                for (Map.Entry<String, Long> e : counters.entrySet()) {
                    String name = e.getKey();
                    long value = e.getValue();
                    PerfCounter.newPerfCounter(name).set(value);
                    if (PRINT_COUNTERS)
                        System.out.println(name + " = " + value);
                }
                long elapsedTotal = currentTime - startTime;
                PerfCounter.newPerfCounter(totalTimeName).set(elapsedTotal);
                if (PRINT_COUNTERS)
                    System.out.println(totalTimeName + " = " + elapsedTotal);
            }
        }
    }
}
/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.memory;

import java.util.*;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class FileMapInfo {
  private static FileMapHeader headerObj;

  private static Address rwRegionBaseAddress;
  private static Address rwRegionEndAddress;
  private static Address vtablesIndex;

  private static Map<Address, Type> vTableTypeMap;

  private static Type metadataTypeArray[];

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  static Address getStatic_AddressField(Type type, String fieldName) {
    AddressField field = type.getAddressField(fieldName);
    return field.getValue();
  }

  static Address get_AddressField(Type type, Address instance, String fieldName) {
    AddressField field = type.getAddressField(fieldName);
    return field.getValue(instance);
  }

  static long get_CIntegerField(Type type, Address instance, String fieldName) {
    CIntegerField field = type.getCIntegerField(fieldName);
    return field.getValue(instance);
  }

  static Address get_CDSFileMapRegion(Type FileMapHeader_type, Address header, int index) {
    AddressField regionsField = FileMapHeader_type.getAddressField("_regions[0]");

    long offset = regionsField.getOffset();
    Address regions_0 = header.addOffsetTo(offset);
    return regions_0.addOffsetTo(index * regionsField.getSize());
  }

  private static void initialize(TypeDataBase db) {
    vTableTypeMap = null; 

    Type FileMapInfo_type = db.lookupType("FileMapInfo");
    Type FileMapHeader_type = db.lookupType("FileMapHeader");
    Type CDSFileMapRegion_type = db.lookupType("CDSFileMapRegion");

    Address info = getStatic_AddressField(FileMapInfo_type, "_current_info");
    Address header = get_AddressField(FileMapInfo_type, info, "_header");
    headerObj = VMObjectFactory.newObject(FileMapHeader.class, header);

    Address mapped_base_address = get_AddressField(FileMapHeader_type, header, "_mapped_base_address");
    long cloned_vtable_offset = get_CIntegerField(FileMapHeader_type, header, "_cloned_vtables_offset");
    vtablesIndex = mapped_base_address.addOffsetTo(cloned_vtable_offset);

    Address rw_region = get_CDSFileMapRegion(FileMapHeader_type, header, 0);
    rwRegionBaseAddress = get_AddressField(CDSFileMapRegion_type, rw_region, "_mapped_base");
    long used = get_CIntegerField(CDSFileMapRegion_type, rw_region, "_used");
    rwRegionEndAddress = rwRegionBaseAddress.addOffsetTo(used);

    populateMetadataTypeArray(db);
  }

  private static void populateMetadataTypeArray(TypeDataBase db) {
    metadataTypeArray = new Type[9];

    metadataTypeArray[0] = db.lookupType("ConstantPool");
    metadataTypeArray[1] = db.lookupType("InstanceKlass");
    metadataTypeArray[2] = db.lookupType("InstanceClassLoaderKlass");
    metadataTypeArray[3] = db.lookupType("InstanceMirrorKlass");
    metadataTypeArray[4] = db.lookupType("InstanceRefKlass");
    metadataTypeArray[5] = db.lookupType("InstanceStackChunkKlass");
    metadataTypeArray[6] = db.lookupType("Method");
    metadataTypeArray[7] = db.lookupType("ObjArrayKlass");
    metadataTypeArray[8] = db.lookupType("TypeArrayKlass");
  }

  public FileMapHeader getHeader() {
    return headerObj;
  }

  public boolean inCopiedVtableSpace(Address vptrAddress) {
    FileMapHeader fmHeader = getHeader();
    return fmHeader.inCopiedVtableSpace(vptrAddress);
  }

  public Type getTypeForVptrAddress(Address vptrAddress) {
    if (vTableTypeMap == null) {
      getHeader().createVtableTypeMapping();
    }
    return vTableTypeMap.get(vptrAddress);
  }



  public static class FileMapHeader extends VMObject {

    public FileMapHeader(Address addr) {
      super(addr);
    }

    public boolean inCopiedVtableSpace(Address vptrAddress) {
      if (vptrAddress == null) {
        return false;
      }
      if (vptrAddress.greaterThan(rwRegionBaseAddress) &&
          vptrAddress.lessThanOrEqual(rwRegionEndAddress)) {
        return true;
      }
      return false;
    }

    public void createVtableTypeMapping() {
      vTableTypeMap = new HashMap<Address, Type>();
      long addressSize = VM.getVM().getAddressSize();


      for (int i=0; i < metadataTypeArray.length; i++) {
        Address vtableInfoAddress = vtablesIndex.getAddressAt(i * addressSize); 
        Address vtableAddress = vtableInfoAddress.addOffsetTo(addressSize); 
        vTableTypeMap.put(vtableAddress, metadataTypeArray[i]);
      }
    }
  }
}
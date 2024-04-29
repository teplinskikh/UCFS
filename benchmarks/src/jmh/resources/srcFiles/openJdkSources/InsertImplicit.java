/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:all,-missing -ref InsertImplicit.out InsertImplicit.java
 */


/**
 * </p>
 * <i> <blockquote> abc </blockquote> </i>
 */
public class InsertImplicit { }
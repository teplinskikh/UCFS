/*
 * @test /nodynamiccopyright/
 * @bug 4851039
 * @summary explicit type arguments
 * @author gafter
 *
 * @compile/fail/ref=Newarg1.out -XDrawDiagnostics Newarg1.java
 */


class T<X> {

    <K> T(K x) {
    }

    public static void meth() {
        new <Integer>T<Float>("");
    }
}
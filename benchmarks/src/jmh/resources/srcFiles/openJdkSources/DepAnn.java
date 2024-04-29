/*
 * @test /nodynamiccopyright/
 * @bug 4986256
 * @compile/ref=DepAnn.out -XDrawDiagnostics -Xlint:all DepAnn.java
 */

/** @deprecated */
class DepAnn
{
    /** @deprecated */
    void m1(int i) {
    }
}

@SuppressWarnings("dep-ann")
/** @deprecated */
class DepAnn1
{
    /** @deprecated */
    void m1(int i) {
        /** @deprecated */
        int x = 3;
    }
}

class DepAnn2
{
    @SuppressWarnings("dep-ann")
    /** @deprecated */
    class Bar {
        /** @deprecated */
        void m1(int i) {
        }
    }

    @SuppressWarnings("dep-ann")
    /** @deprecated */
    void m2(int i) {
    }


    @SuppressWarnings("dep-ann")
    static int x = new DepAnn2() {
            /** @deprecated */
            int m1(int i) {
                return 0;
            }
        }.m1(0);

}

/** @deprecated */
class DepAnn3 extends DepAnn1
{
    /** @deprecated */
    void m1(int i) {
    }
}
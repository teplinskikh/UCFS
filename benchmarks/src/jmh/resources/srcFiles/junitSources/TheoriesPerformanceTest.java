package org.junit.tests.experimental.theories.runner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.junit.experimental.results.PrintableResult.testResult;
import static org.junit.experimental.results.ResultMatchers.isSuccessful;

import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

public class TheoriesPerformanceTest {
    @RunWith(Theories.class)
    public static class UpToTen {
        @DataPoints
        public static int[] ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        @Theory
        public void threeInts(int x, int y, int z) {
        }
    }

    private static final boolean TESTING_PERFORMANCE = false;

    @Test
    public void tryCombinationsQuickly() {
        assumeTrue(TESTING_PERFORMANCE);
        assertThat(testResult(UpToTen.class), isSuccessful());
    }
}
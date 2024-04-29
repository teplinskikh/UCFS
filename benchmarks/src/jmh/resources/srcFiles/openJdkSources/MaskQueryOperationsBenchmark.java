package org.openjdk.bench.jdk.incubator.vector;

import java.util.Random;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class MaskQueryOperationsBenchmark {
    @Param({"128","256","512"})
    int bits;

    @Param({"1","2","3"})
    int inputs;

    VectorSpecies<Byte> bspecies;
    VectorSpecies<Short> sspecies;
    VectorSpecies<Integer> ispecies;
    VectorSpecies<Long> lspecies;
    VectorMask<Byte> bmask;
    VectorMask<Short> smask;
    VectorMask<Integer> imask;
    VectorMask<Long> lmask;
    boolean [] mask_arr;

    static final boolean [] mask_avg_case = {
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false
    };

    static final boolean [] mask_best_case  = {
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true
    };

    static final boolean [] mask_worst_case  = {
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false
    };

    @Setup(Level.Trial)
    public void BmSetup() {
        bspecies = VectorSpecies.of(byte.class, VectorShape.forBitSize(bits));
        sspecies = VectorSpecies.of(short.class, VectorShape.forBitSize(bits));
        ispecies = VectorSpecies.of(int.class, VectorShape.forBitSize(bits));
        lspecies = VectorSpecies.of(long.class, VectorShape.forBitSize(bits));

        if( 1 == inputs) {
          mask_arr = mask_best_case;
        } else if ( 2 == inputs ) {
          mask_arr = mask_worst_case;
        } else {
          mask_arr = mask_avg_case;
        }

        bmask   = VectorMask.fromArray(bspecies, mask_arr, 0);
        smask   = VectorMask.fromArray(sspecies, mask_arr, 0);
        imask   = VectorMask.fromArray(ispecies, mask_arr, 0);
        lmask   = VectorMask.fromArray(lspecies, mask_arr, 0);
    }

    @Benchmark
    public int testTrueCountByte(Blackhole bh) {
        return bmask.trueCount();
    }

    @Benchmark
    public int testTrueCountShort(Blackhole bh) {
        return smask.trueCount();
    }
    @Benchmark
    public int testTrueCountInt(Blackhole bh) {
        return imask.trueCount();
    }
    @Benchmark
    public int testTrueCountLong(Blackhole bh) {
        return lmask.trueCount();
    }

    @Benchmark
    public int testFirstTrueByte(Blackhole bh) {
        return bmask.firstTrue();
    }

    @Benchmark
    public int testFirstTrueShort(Blackhole bh) {
        return smask.firstTrue();
    }
    @Benchmark
    public int testFirstTrueInt(Blackhole bh) {
        return imask.firstTrue();
    }
    @Benchmark
    public int testFirstTrueLong(Blackhole bh) {
        return lmask.firstTrue();
    }

    @Benchmark
    public int testLastTrueByte(Blackhole bh) {
        return bmask.lastTrue();
    }

    @Benchmark
    public int testLastTrueShort(Blackhole bh) {
        return smask.lastTrue();
    }
    @Benchmark
    public int testLastTrueInt(Blackhole bh) {
        return imask.lastTrue();
    }
    @Benchmark
    public int testLastTrueLong(Blackhole bh) {
        return lmask.lastTrue();
    }

    @Benchmark
    public long testToLongByte(Blackhole bh) {
        return bmask.toLong();
    }

    @Benchmark
    public long testToLongShort(Blackhole bh) {
        return smask.toLong();
    }
    @Benchmark
    public long testToLongInt(Blackhole bh) {
        return imask.toLong();
    }
    @Benchmark
    public long testToLongLong(Blackhole bh) {
        return lmask.toLong();
    }

}
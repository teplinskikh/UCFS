/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.hash;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import java.util.Random;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Benchmarks for comparing {@link Checksum}s and {@link HashFunction}s that wrap {@link Checksum}s.
 *
 * <p>Parameters for the benchmark are:
 *
 * <ul>
 *   <li>size: The length of the byte array to hash.
 * </ul>
 *
 * @author Colin Decker
 */
public class ChecksumBenchmark {

  private static final int RANDOM_SEED = new Random().nextInt();

  @Param({"10", "1000", "100000", "1000000"})
  private int size;

  private byte[] testBytes;

  @BeforeExperiment
  void setUp() {
    testBytes = new byte[size];
    new Random(RANDOM_SEED).nextBytes(testBytes);
  }


  @Benchmark
  byte crc32HashFunction(int reps) {
    return runHashFunction(reps, Hashing.crc32());
  }

  @Benchmark
  byte crc32Checksum(int reps) throws Exception {
    byte result = 0x01;
    for (int i = 0; i < reps; i++) {
      CRC32 checksum = new CRC32();
      checksum.update(testBytes, 0, testBytes.length);
      result = (byte) (result ^ checksum.getValue());
    }
    return result;
  }


  @Benchmark
  byte crc32cHashFunction(int reps) {
    return runHashFunction(reps, Hashing.crc32c());
  }


  @Benchmark
  byte adler32HashFunction(int reps) {
    return runHashFunction(reps, Hashing.adler32());
  }

  @Benchmark
  byte adler32Checksum(int reps) throws Exception {
    byte result = 0x01;
    for (int i = 0; i < reps; i++) {
      Adler32 checksum = new Adler32();
      checksum.update(testBytes, 0, testBytes.length);
      result = (byte) (result ^ checksum.getValue());
    }
    return result;
  }


  private byte runHashFunction(int reps, HashFunction hashFunction) {
    byte result = 0x01;
    result ^= Hashing.crc32().hashInt(reps).asBytes()[0];
    result ^= Hashing.adler32().hashInt(reps).asBytes()[0];
    for (int i = 0; i < reps; i++) {
      result ^= hashFunction.hashBytes(testBytes).asBytes()[0];
    }
    return result;
  }
}
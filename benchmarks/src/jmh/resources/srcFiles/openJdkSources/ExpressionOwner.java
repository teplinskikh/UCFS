/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xpath.internal;

/**
 * Classes that implement this interface own an expression, which
 * can be rewritten.
 */
public interface ExpressionOwner
{
  /**
   * Get the raw Expression object that this class wraps.
   *
   * @return the raw Expression object, which should not normally be null.
   */
  public Expression getExpression();

  /**
   * Set the raw expression object for this object.
   *
   * @param exp the raw Expression object, which should not normally be null.
   */
  public void setExpression(Expression exp);


}
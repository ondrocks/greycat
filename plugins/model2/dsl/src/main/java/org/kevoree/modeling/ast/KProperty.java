/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kevoree.modeling.ast;

import java.util.Map;

public interface KProperty extends Comparable {

    String name();

    String type();

    String algorithm();

    void setAlgorithm(String alg);

    KDependency[] dependencies();

    void addIndex(KIndex index);

    KIndex[] indexes();

    void addDependency(KDependency dependency);

    Map<String, String> parameters();

    void addParameter(String param, String value);

    boolean derived();

    void setDerived();

    boolean learned();

    void setLearned();

    boolean global();

    void setGlobal();

}

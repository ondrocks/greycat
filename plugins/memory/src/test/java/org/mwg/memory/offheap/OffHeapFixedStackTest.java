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
package org.mwg.memory.offheap;

import greycat.memory.OffHeapConstants;
import greycat.memory.OffHeapFixedStack;
import org.junit.Assert;
import org.junit.Test;
import org.mwg.chunk.Stack;
import org.mwg.internal.chunk.AbstractFixedStackTest;

public class OffHeapFixedStackTest extends AbstractFixedStackTest {

    @Test
    public void offHeapFixedStackTest() {
        Stack stack = new OffHeapFixedStack(CAPACITY, true);
        test(stack);
        stack.free();

        if (OffHeapConstants.DEBUG_MODE) {
            Assert.assertEquals(OffHeapConstants.SEGMENTS.size(), 0);
        }

    }

}

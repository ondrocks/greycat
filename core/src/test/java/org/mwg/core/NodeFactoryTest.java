package org.mwg.core;

import org.junit.Assert;
import org.junit.Test;
import org.mwg.*;
import org.mwg.Graph;
import org.mwg.Node;
import org.mwg.core.chunk.offheap.OffHeapByteArray;
import org.mwg.core.chunk.offheap.OffHeapDoubleArray;
import org.mwg.core.chunk.offheap.OffHeapLongArray;
import org.mwg.core.chunk.offheap.OffHeapStringArray;
import org.mwg.plugin.AbstractNode;
import org.mwg.plugin.NodeFactory;
import org.mwg.core.utility.Unsafe;

import java.lang.reflect.Array;

public class NodeFactoryTest implements NodeFactory {

    @Override
    public String name() {
        return "HelloWorldNode";
    }

    interface ExNode extends org.mwg.Node {
        String sayHello();
    }

    class ExNodeImpl extends AbstractNode implements ExNode {

        public ExNodeImpl(long p_world, long p_time, long p_id, Graph p_graph, long[] currentResolution) {
            super(p_world, p_time, p_id, p_graph, currentResolution);
        }

        @Override
        public Object get(String propertyName) {
            if (propertyName.equals("hello")) {
                return "world";
            }
            return super.get(propertyName);
        }

        @Override
        public String sayHello() {
            return "HelloWorld";
        }
    }

    @Override
    public org.mwg.Node create(long world, long time, long id, org.mwg.Graph graph, long[] currentResolution) {
        return new ExNodeImpl(world, time, id, graph, currentResolution);
    }

    @Test
    public void heapTest() {
        test(GraphBuilder.builder().withScheduler(new NoopScheduler()).withFactory(this).build());
    }

    @Test
    public void offHeapTest() {
        OffHeapByteArray.alloc_counter = 0;
        OffHeapDoubleArray.alloc_counter = 0;
        OffHeapLongArray.alloc_counter = 0;
        OffHeapStringArray.alloc_counter = 0;

        Unsafe.DEBUG_MODE = true;

        test(GraphBuilder.builder().withScheduler(new NoopScheduler()).withOffHeapMemory().withMemorySize(10_000).withAutoSave(20).withFactory(this).build());

        Assert.assertTrue(OffHeapByteArray.alloc_counter == 0);
        Assert.assertTrue(OffHeapDoubleArray.alloc_counter == 0);
        Assert.assertTrue(OffHeapLongArray.alloc_counter == 0);
        Assert.assertTrue(OffHeapStringArray.alloc_counter == 0);
    }

    private void test(Graph graph) {

        graph.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean result) {
                Node specializedNode = graph.newNode(0, 0, "HelloWorldNode");

                String hw = (String) specializedNode.get("hello");
                Assert.assertTrue(hw.equals("world"));

                Node parent = graph.newNode(0, 0);
                parent.add("children", specializedNode);
                parent.rel("children", new Callback<Node[]>() {
                    @Override
                    public void on(Node[] result) {
                        Assert.assertEquals("HelloWorld", ((ExNode) result[0]).sayHello());
                    }
                });

                specializedNode.free();
                graph.disconnect(null);
            }
        });
    }

}
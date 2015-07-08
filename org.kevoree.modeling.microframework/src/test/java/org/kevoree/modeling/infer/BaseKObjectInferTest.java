package org.kevoree.modeling.infer;

import org.junit.Test;
import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.KModel;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.KObjectInfer;
import org.kevoree.modeling.infer.impl.StatInferAlg;
import org.kevoree.modeling.meta.KMetaClass;
import org.kevoree.modeling.meta.KMetaModel;
import org.kevoree.modeling.meta.KPrimitiveTypes;
import org.kevoree.modeling.meta.impl.MetaModel;

public class BaseKObjectInferTest {

    private KMetaModel createMetaModel() {
        KMetaModel metaModel = new MetaModel("InferTestMM");
        KMetaClass metaClassSensor = metaModel.addMetaClass("Sensor");
        metaClassSensor.addAttribute("name", KPrimitiveTypes.STRING);
        metaClassSensor.addAttribute("value", KPrimitiveTypes.DOUBLE);
        metaClassSensor.addReference("siblings", metaClassSensor, null, true);

        KMetaClass inferAvg = metaModel.addInferMetaClass("SensorProfile", new StatInferAlg());
        inferAvg.addDependency("sensors", metaClassSensor, null);
        inferAvg.addInput("value", "sensors | @value");
        inferAvg.addOutput("avg", KPrimitiveTypes.DOUBLE);

        return metaModel;
    }

    @Test
    public void test() {
        KMetaModel mm = createMetaModel();
        KModel model = mm.model();
        model.connect(new KCallback() {
            @Override
            public void on(Object o) {
                KObject sensor1 = model.createByName("Sensor", 0, 0);
                sensor1.setByName("name", "sensor#1");
                sensor1.setByName("value", 0.42);

                KObject sensor2 = model.createByName("Sensor", 0, 0);
                sensor2.setByName("name", "sensor#2");
                sensor2.setByName("value", 0.22);

                KObjectInfer sensorProfile = (KObjectInfer) model.createByName("SensorProfile", 0, 0);
                sensorProfile.train(new KObject[]{sensor1}, null, null);
                sensorProfile.train(new KObject[]{sensor2}, null, null);


            }
        });
    }

}

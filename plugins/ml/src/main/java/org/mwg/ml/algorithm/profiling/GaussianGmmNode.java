package org.mwg.ml.algorithm.profiling;

import org.mwg.Callback;
import org.mwg.Graph;
import org.mwg.Node;
import org.mwg.Type;
import org.mwg.ml.AbstractMLNode;
import org.mwg.ml.ProfilingNode;
import org.mwg.ml.common.matrix.Matrix;
import org.mwg.ml.common.matrix.operation.MultivariateNormalDistribution;
import org.mwg.plugin.NodeFactory;
import org.mwg.plugin.NodeState;
import org.mwg.task.*;

public class GaussianGmmNode extends AbstractMLNode implements ProfilingNode {


    //Getters and setters
    public final static String NAME = "GaussianGmm";
    public static final String MIN = "min";
    public static final String MAX = "max";
    public static final String AVG = "avg";
    public static final String COV = "cov";


    //Mixture model params
    public static final String LEVEL_KEY = "_level";  //Current level of the gaussian node, top level is the highest number, bottom leaves have level 0.
    public static final int LEVEL_DEF = 0;
    public static final String WIDTH_KEY = "_width";  // Nuber of children after compressing, note that Factor x wodth is the max per level tolerated before compressing
    public static final int WIDTH_DEF = 10;
    public static final String COMPRESSION_FACTOR_KEY = "_compression";  // Factor of subnodes allowed before starting compression. For ex: 2 => 2x Width before compressing to width
    public static final double COMPRESSION_FACTOR_DEF = 2;
    public static final String COMPRESSION_ITER_KEY = "_compressioniter"; //Number of time to iterate K-means before finding the best compression
    public static final int COMPRESSION_ITER_DEF = 10;
    public static final String THRESHOLD_KEY = "_threshold";  //Factor of distance before check inside fail
    public static final double THRESHOLD_DEF = 3;

    public static final String PRECISION_KEY = "_precision"; //Default covariance matrix for a dirac function

    //Gaussian keys
    public static final String INTERNAL_SUBGAUSSIAN_KEY = "_subGaussian";
    private static final String INTERNAL_SUM_KEY = "_sum";
    private static final String INTERNAL_SUMSQUARE_KEY = "_sumSquare";
    private static final String INTERNAL_TOTAL_KEY = "_total";
    private static final String INTERNAL_MIN_KEY = "_min";
    private static final String INTERNAL_MAX_KEY = "_max";


    //Factory of the class integrated
    public static class Factory implements NodeFactory {

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public Node create(long world, long time, long id, Graph graph, long[] initialResolution) {
            return new GaussianGmmNode(world, time, id, graph, initialResolution);
        }
    }

    public GaussianGmmNode(long p_world, long p_time, long p_id, Graph p_graph, long[] currentResolution) {
        super(p_world, p_time, p_id, p_graph, currentResolution);
    }


    @Override
    public void setProperty(String propertyName, byte propertyType, Object propertyValue) {
        if (propertyName.equals(LEVEL_KEY)) {
            super.setPropertyWithType(propertyName, propertyType, propertyValue, Type.INT);
        } else if (propertyName.equals(WIDTH_KEY)) {
            super.setPropertyWithType(propertyName, propertyType, propertyValue, Type.INT);
        } else if (propertyName.equals(COMPRESSION_FACTOR_KEY)) {
            super.setPropertyWithType(propertyName, propertyType, propertyValue, Type.DOUBLE);
        } else if (propertyName.equals(THRESHOLD_KEY)) {
            super.setPropertyWithType(propertyName, propertyType, propertyValue, Type.DOUBLE);
        } else if (propertyName.equals(PRECISION_KEY)) {
            super.setPropertyWithType(propertyName, propertyType, propertyValue, Type.DOUBLE_ARRAY);
        } else {
            super.setProperty(propertyName, propertyType, propertyValue);
        }
    }

    @Override
    public byte type(String attributeName) {
        if (attributeName.equals(AVG)) {
            return Type.DOUBLE_ARRAY;
        } else if (attributeName.equals(MIN)) {
            return Type.DOUBLE_ARRAY;
        } else if (attributeName.equals(MAX)) {
            return Type.DOUBLE_ARRAY;
        } else if (attributeName.equals(COV)) {
            return Type.DOUBLE_ARRAY;
        } else if (attributeName.equals(PRECISION_KEY)) {
            return Type.DOUBLE_ARRAY;
        } else {
            return super.type(attributeName);
        }
    }

    @Override
    public Object get(String attributeName) {
        if (attributeName.equals(AVG)) {
            return getAvg();
        } else if (attributeName.equals(MIN)) {
            return getMin();
        } else if (attributeName.equals(MAX)) {
            return getMax();
        } else if (attributeName.equals(MAX)) {
            return getMax();
        } else if (attributeName.equals(COV)) {
            NodeState resolved = this._resolver.resolveState(this, true);

            double[] initialPrecision = (double[]) resolved.getFromKey(PRECISION_KEY);
            int nbfeature = this.getNumberOfFeatures();
            if (initialPrecision == null) {
                initialPrecision = new double[nbfeature];
                for (int i = 0; i < nbfeature; i++) {
                    initialPrecision[i] = 1;
                }
            }
            return getCovariance(getAvg(), initialPrecision);
        } else {
            return super.get(attributeName);
        }
    }


    @Override
    public void learn(Callback<Boolean> callback) {
        extractFeatures(new Callback<double[]>() {
            @Override
            public void on(double[] values) {
                //ToDO temporal hack to avoid features extractions - to remove later
                learnVector(values, callback);
            }
        });
    }


    //ToDO temporal hack to avoid features extractions - to remove later
    public void learnVector(double[] values, Callback<Boolean> callback) {
        NodeState resolved = this._resolver.resolveState(this, true);
        final int width = resolved.getFromKeyWithDefault(WIDTH_KEY, WIDTH_DEF);
        final double compressionFactor = resolved.getFromKeyWithDefault(COMPRESSION_FACTOR_KEY, COMPRESSION_FACTOR_DEF);
        final int compressionIter = resolved.getFromKeyWithDefault(COMPRESSION_ITER_KEY, COMPRESSION_ITER_DEF);
        double[] initialPrecision = (double[]) resolved.getFromKey(PRECISION_KEY);
        if (initialPrecision == null) {
            initialPrecision = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                initialPrecision[i] = 1;
            }
        }
        final double[] precisions = initialPrecision;
        final double threshold = resolved.getFromKeyWithDefault(THRESHOLD_KEY, THRESHOLD_DEF);


        Task creationTask = graph().newTask().then(new Action() {
            @Override
            public void eval(TaskContext context) {
                GaussianGmmNode node = (GaussianGmmNode) context.getVariable("starterNode");
                //System.out.println("Vector: " + values[0] + " " + values[1]);
                node.internallearn(values, width, compressionFactor, compressionIter, precisions, threshold, true);
            }
        });


        Task traverse = graph().newTask();
        traverse.fromVar("starterNode").traverse(INTERNAL_SUBGAUSSIAN_KEY).then(new Action() {
            @Override
            public void eval(TaskContext context) {
                Node[] result = (Node[]) context.getPreviousResult();
                GaussianGmmNode parent = (GaussianGmmNode) context.getVariable("starterNode");
                GaussianGmmNode resultChild = filter(result, values, precisions, threshold, parent.getLevel() - 1.0);
                if (resultChild != null) {
                    parent.internallearn(values, width, compressionFactor, compressionIter, precisions, threshold, false);
                    context.setVariable("continueLoop", true);
                    context.setVariable("starterNode", resultChild);
                } else {
                    context.setVariable("continueLoop", false);
                }
            }
        })
                .ifThen(new TaskFunctionConditional() {
                    @Override
                    public boolean eval(TaskContext context) {
                        return (boolean) context.getVariable("continueLoop");
                    }
                }, traverse);

        Task mainTask = graph().newTask().from(this).asVar("starterNode").executeSubTask(traverse).executeSubTask(creationTask);
        mainTask.executeThen(new Action() {
            @Override
            public void eval(TaskContext context) {
                if(callback!=null) {
                    callback.on(true);
                }
            }
        });
    }


    private boolean checkInside(final double[] min, final double[] max, final double[] precisions, double threshold, double level) {
        threshold = threshold + level * 0.707;

        double[] avg = getAvg();
        boolean result = true;
        double[] cov = getCovarianceArray(avg, precisions);


        for (int i = 0; i < min.length; i++) {
            cov[i] = Math.sqrt(cov[i]);
            if (((avg[i] + cov[i]) < (min[i] - threshold * precisions[i])) || ((avg[i] - cov[i]) > (max[i] + threshold * precisions[i]))) {
                result = false;
                break;
            }
        }
        return result;
    }

    //ToDo need to be replaced by gaussian distances !!
    private GaussianGmmNode filter(final Node[] result, final double[] features, final double[] precisions, double threshold, double level) {
        threshold = threshold + level * 0.707;


        if (result == null || result.length == 0) {
            return null;
        }
        double[] distances = new double[result.length];
        double min = Double.MAX_VALUE;
        int index = 0;
        for (int i = 0; i < result.length; i++) {
            GaussianGmmNode temp = ((GaussianGmmNode) result[i]);
            double[] avg = temp.getAvg();
            distances[i] = distance(features, avg, temp.getCovarianceArray(avg, precisions));
            if (distances[i] < min) {
                min = distances[i];
                index = i;
            }
        }
        if (min < threshold) {
            return ((GaussianGmmNode) result[index]);
        } else {
            return null;
        }
    }

    @Override
    public void predict(Callback<double[]> callback) {

    }


    public int getLevel() {
        return this._resolver.resolveState(this, true).getFromKeyWithDefault(LEVEL_KEY, LEVEL_DEF);
    }

    public int getWidth() {
        return this._resolver.resolveState(this, true).getFromKeyWithDefault(WIDTH_KEY, WIDTH_DEF);
    }

    public double getCompressionFactor() {
        return this._resolver.resolveState(this, true).getFromKeyWithDefault(COMPRESSION_FACTOR_KEY, COMPRESSION_FACTOR_DEF);
    }

    public int getCompressionIter() {
        return this._resolver.resolveState(this, true).getFromKeyWithDefault(COMPRESSION_ITER_KEY, COMPRESSION_ITER_DEF);
    }


    private void updateLevel(final int newLevel) {
        super.set(LEVEL_KEY, newLevel);
        if (newLevel == 0) {
            super.set(INTERNAL_SUBGAUSSIAN_KEY, new long[0]);
        } else {
            super.rel(INTERNAL_SUBGAUSSIAN_KEY, new Callback<Node[]>() {
                @Override
                public void on(Node[] result) {
                    for (int i = 0; i < result.length; i++) {
                        GaussianGmmNode g = (GaussianGmmNode) result[i];
                        g.updateLevel(newLevel - 1);
                        result[i].free();
                    }
                }
            });
        }
    }

    private GaussianGmmNode createLevel(double[] values, final int level, final int width, final double compressionFactor, final int compressionIter, final double[] precisions, final double threshold) {
        GaussianGmmNode g = (GaussianGmmNode) graph().newTypedNode(this.world(), this.time(), "GaussianGmm");
        g.set(LEVEL_KEY, level);
        g.internallearn(values, width, compressionFactor, compressionIter, precisions, threshold, false); //dirac
        super.add(INTERNAL_SUBGAUSSIAN_KEY, g);
        return g;
    }

    private void checkAndCompress(final int width, final double compressionFactor, final int compressionIter, final double[] precisions, final double threshold) {

        final Node selfPointer = this;

        long[] subgaussians = (long[]) super.get(INTERNAL_SUBGAUSSIAN_KEY);
        if (subgaussians != null && subgaussians.length >= compressionFactor * width) {
            super.rel(INTERNAL_SUBGAUSSIAN_KEY, new Callback<Node[]>() {
                @Override
                //result.length hold the original subgaussian number, and width is after compression
                public void on(Node[] result) {
                    GaussianGmmNode[] subgauss = new GaussianGmmNode[result.length];
                    double[][] data = new double[result.length][];
                    for (int i = 0; i < result.length; i++) {
                        subgauss[i] = (GaussianGmmNode) result[i];
                        data[i] = subgauss[i].getAvg();
                    }

                    //Cluster the different gaussians
                    KMeans clusteringEngine = new KMeans();
                    int[][] clusters = clusteringEngine.getClusterIds(data, width, compressionIter, precisions);

                    //Select the ones which will remain as head by the maximum weight
                    GaussianGmmNode[] mainClusters = new GaussianGmmNode[width];
                    for (int i = 0; i < width; i++) {
                        if (clusters[i] != null && clusters[i].length > 0) {
                            int max = 0;
                            int maxpos = 0;
                            for (int j = 0; j < clusters[i].length; j++) {
                                int x = subgauss[clusters[i][j]].getTotal();
                                if (x > max) {
                                    max = x;
                                    maxpos = clusters[i][j];
                                }
                            }
                            mainClusters[i] = subgauss[maxpos];
                        }
                    }


                    //move the nodes
                    for (int i = 0; i < width; i++) {
                        //if the main cluster node contains only 1 sample, it needs to clone itself in itself
                        if (clusters[i].length > 1 && mainClusters[i].getTotal() == 1 && mainClusters[i].getLevel() > 0) {
                            mainClusters[i].createLevel(mainClusters[i].getAvg(), mainClusters[i].getLevel() - 1, width, compressionFactor, compressionIter, precisions, threshold).free();
                        }

                        if (clusters[i] != null && clusters[i].length > 0) {
                            for (int j = 0; j < clusters[i].length; j++) {
                                GaussianGmmNode g = subgauss[clusters[i][j]];
                                if (g != mainClusters[i]) {
                                    mainClusters[i].move(g);
                                    selfPointer.remove(INTERNAL_SUBGAUSSIAN_KEY, g);
                                    g.free();
                                }
                            }
                            mainClusters[i].checkAndCompress(width, compressionFactor, compressionIter, precisions, threshold);
                        }
                    }

                    for (int i = 0; i < result.length; i++) {
                        result[i].free();
                    }
                }
            });
        }
    }


    private void move(GaussianGmmNode subgaus) {
        //manage total
        int total = getTotal();
        int level = getLevel();


        double[] sum = getSum();
        double[] min = getMin();
        double[] max = getMax();
        double[] sumsquares = getSumSquares();


        //Start the merging phase

        total = total + subgaus.getTotal();

        double[] sum2 = subgaus.getSum();
        double[] min2 = subgaus.getMin();
        double[] max2 = subgaus.getMax();
        double[] sumsquares2 = subgaus.getSumSquares();

        for (int i = 0; i < sum.length; i++) {
            sum[i] = sum[i] + sum2[i];
            if (min2[i] < min[i]) {
                min[i] = min2[i];
            }
            if (max2[i] > max[i]) {
                max[i] = max2[i];
            }
        }

        for (int i = 0; i < sumsquares.length; i++) {
            sumsquares[i] = sumsquares[i] + sumsquares2[i];
        }

        //Store everything
        set(INTERNAL_TOTAL_KEY, total);
        set(INTERNAL_SUM_KEY, sum);
        set(INTERNAL_MIN_KEY, min);
        set(INTERNAL_MAX_KEY, max);
        set(INTERNAL_SUMSQUARE_KEY, sumsquares);

        //Add the subGaussian to the relationship
        if (level > 0) {
            long[] subrelations = (long[]) subgaus.get(INTERNAL_SUBGAUSSIAN_KEY);
            if (subrelations == null) {
                subgaus.updateLevel(level - 1);
                super.add(INTERNAL_SUBGAUSSIAN_KEY, subgaus);
            } else {
                long[] oldrel = (long[]) this.get(INTERNAL_SUBGAUSSIAN_KEY);
                if (oldrel == null) {
                    oldrel = new long[0];
                }
                long[] newrelations = new long[oldrel.length + subrelations.length];
                System.arraycopy(oldrel, 0, newrelations, 0, oldrel.length);
                System.arraycopy(subrelations, 0, newrelations, oldrel.length, subrelations.length);
                set(INTERNAL_SUBGAUSSIAN_KEY, newrelations);
            }


        }
    }


    public void query(int level, double[] min, double[] max, Callback<ProbaDistribution> callback) {
        int nbfeature = this.getNumberOfFeatures();

        if (nbfeature == 0) {
            callback.on(null);
            return;
        }

        NodeState resolved = this._resolver.resolveState(this, true);

        double[] initialPrecision = (double[]) resolved.getFromKey(PRECISION_KEY);
        if (initialPrecision == null) {
            initialPrecision = new double[nbfeature];
            for (int i = 0; i < nbfeature; i++) {
                initialPrecision[i] = 1;
            }
        }


        if (min == null) {
            min = getMin();
        }
        if (max == null) {
            max = getMax();
        }

        for (int i = 0; i < nbfeature; i++) {
            if ((max[i] - min[i]) < initialPrecision[i]) {
                min[i] = min[i] - initialPrecision[i];
                max[i] = min[i] + 2 * initialPrecision[i];
            }
        }

        final double[] finalMin = min;
        final double[] finalMax = max;
        final double[] err = initialPrecision;
        final double threshold = resolved.getFromKeyWithDefault(THRESHOLD_KEY, THRESHOLD_DEF);

        //At this point we have min and max at least with 2xerr of difference

        Task deepTraverseTask = graph().newTask();
        final int parentLevel = this.getLevel();

        deepTraverseTask.from(new Node[]{this});
        for (int i = 0; i < this.getLevel() - level; i++) {
            deepTraverseTask.traverseOrKeep(INTERNAL_SUBGAUSSIAN_KEY);
            int finalI = i;
            deepTraverseTask.select(new TaskFunctionSelect() {
                @Override
                public boolean select(Node node) {
                    return ((GaussianGmmNode) node).checkInside(finalMin, finalMax, err, threshold, parentLevel - finalI);
                }
            });
        }
        deepTraverseTask.then(new Action() {
            @Override
            public void eval(TaskContext context) {

                Node[] leaves = (Node[]) context.getPreviousResult();   // to check
                Matrix covBackup = new Matrix(null, nbfeature, nbfeature);
                for (int i = 0; i < nbfeature; i++) {
                    covBackup.set(i, i, err[i]);
                }
                MultivariateNormalDistribution mvnBackup = new MultivariateNormalDistribution(null, covBackup, false);

                int[] totals = new int[leaves.length];
                int globalTotal = 0;

                MultivariateNormalDistribution[] distributions = new MultivariateNormalDistribution[leaves.length];
                for (int i = 0; i < leaves.length; i++) {
                    GaussianGmmNode temp = ((GaussianGmmNode) leaves[i]);
                    totals[i] = temp.getTotal();
                    globalTotal += totals[i];
                    double[] avg = temp.getAvg();
                    if (totals[i] > 2) {
                        distributions[i] = new MultivariateNormalDistribution(avg, temp.getCovariance(avg, err), false);
                        distributions[i].setMin(temp.getMin());
                        distributions[i].setMax(temp.getMax());
                    } else {
                        distributions[i] = mvnBackup.clone(avg); //this can be optimized later by inverting covBackup only once
                    }
                }
                callback.on(new ProbaDistribution(totals, distributions, globalTotal));
            }
        });

        deepTraverseTask.execute();
    }


    public void generateDistributions(int level, Callback<ProbaDistribution> callback) {
        int nbfeature = this.getNumberOfFeatures();
        if (nbfeature == 0) {
            callback.on(null);
            return;
        }

        NodeState resolved = this._resolver.resolveState(this, true);

        double[] initialPrecision = (double[]) resolved.getFromKey(PRECISION_KEY);
        if (initialPrecision == null) {
            initialPrecision = new double[nbfeature];
            for (int i = 0; i < nbfeature; i++) {
                initialPrecision[i] = 1;
            }
        }
        final double[] err = initialPrecision;

        Task deepTraverseTask = graph().newTask();

        deepTraverseTask.from(new Node[]{this});
        for (int i = 0; i < this.getLevel() - level; i++) {
            deepTraverseTask.traverseOrKeep(INTERNAL_SUBGAUSSIAN_KEY);
        }

        deepTraverseTask.then(new Action() {
            @Override
            public void eval(TaskContext context) {
                Node[] leaves = (Node[]) context.getPreviousResult();   // to check
                Matrix covBackup = new Matrix(null, nbfeature, nbfeature);
                for (int i = 0; i < nbfeature; i++) {
                    covBackup.set(i, i, err[i]);
                }
                MultivariateNormalDistribution mvnBackup = new MultivariateNormalDistribution(null, covBackup, false);

                int[] totals = new int[leaves.length];
                int globalTotal = 0;

                MultivariateNormalDistribution[] distributions = new MultivariateNormalDistribution[leaves.length];
                for (int i = 0; i < leaves.length; i++) {
                    GaussianGmmNode temp = ((GaussianGmmNode) leaves[i]);
                    totals[i] = temp.getTotal();
                    globalTotal += totals[i];
                    double[] avg = temp.getAvg();
                    if (totals[i] > 2) {
                        distributions[i] = new MultivariateNormalDistribution(avg, temp.getCovariance(avg, err), false);
                        distributions[i].setMin(temp.getMin());
                        distributions[i].setMax(temp.getMax());
                    } else {
                        distributions[i] = mvnBackup.clone(avg); //this can be optimized later by inverting covBackup only once
                    }
                }
                callback.on(new ProbaDistribution(totals, distributions, globalTotal));
            }
        });

        deepTraverseTask.execute();
    }

    @Override
    public String toString() {
        return (String) get("name");
//       double[] avg = getAvg();
//        StringBuilder sb = new StringBuilder("[L-" + getLevel() + "]: ");
//        if (avg != null) {
//            NumberFormat formatter = new DecimalFormat("#0.0");
//            for (int i = 0; i < avg.length; i++) {
//                sb.append(formatter.format(avg[i]));
//                sb.append(" ");
//            }
//            sb.append(", total: ").append(getTotal());
//        }
//        return sb.toString();
    }


    private void internallearn(final double[] values, final int width, final double compressionFactor, final int compressionIter, final double[] precisions, double threshold, final boolean createNode) {
        int features = values.length;

        boolean reccursive = false;
        //manage total
        int total = getTotal();
        int level = getLevel();

        //Create dirac
        if (total == 0) {
            double[] sum = new double[features];
            System.arraycopy(values, 0, sum, 0, features);
            total = 1;

            //set total, weight, sum, return
            set(INTERNAL_TOTAL_KEY, total);
            set(INTERNAL_SUM_KEY, sum);
        } else {
            double[] sum;
            double[] min;
            double[] max;
            double[] sumsquares;

            //Upgrade dirac to gaussian
            if (total == 1) {
                //Create getMin, getMax, sumsquares
                sum = (double[]) super.get(INTERNAL_SUM_KEY);
                min = new double[features];
                max = new double[features];
                System.arraycopy(sum, 0, min, 0, features);
                System.arraycopy(sum, 0, max, 0, features);

                sumsquares = new double[features * (features + 1) / 2];
                int count = 0;
                for (int i = 0; i < features; i++) {
                    for (int j = i; j < features; j++) {
                        sumsquares[count] = sum[i] * sum[j];
                        count++;
                    }
                }
                //Self clone to create a sublevel
                if (createNode && level > 0) {
                    GaussianGmmNode newLev = createLevel(sum, level - 1, width, compressionFactor, compressionIter, precisions, threshold);
                    double d = distance(values, sum, precisions);
                    if (d < threshold) {
                        reccursive = true;
                        newLev.internallearn(values, width, compressionFactor, compressionIter, precisions, threshold, createNode);
                    }
                    newLev.free();
                }
            }
            //Otherwise, get previously stored values
            else {
                sum = (double[]) super.get(INTERNAL_SUM_KEY);
                min = (double[]) super.get(INTERNAL_MIN_KEY);
                max = (double[]) super.get(INTERNAL_MAX_KEY);
                sumsquares = (double[]) super.get(INTERNAL_SUMSQUARE_KEY);
            }

            //Update the values
            for (int i = 0; i < features; i++) {
                if (values[i] < min[i]) {
                    min[i] = values[i];
                }

                if (values[i] > max[i]) {
                    max[i] = values[i];
                }
                sum[i] += values[i];
            }

            int count = 0;
            for (int i = 0; i < features; i++) {
                for (int j = i; j < features; j++) {
                    sumsquares[count] += values[i] * values[j];
                    count++;
                }
            }
            total++;
            if (createNode && level > 0 && !reccursive) {
                GaussianGmmNode newLev = createLevel(values, level - 1, width, compressionFactor, compressionIter, precisions, threshold);
                newLev.free();
                checkAndCompress(width, compressionFactor, compressionIter, precisions, threshold);
            }

            //Store everything
            set(INTERNAL_TOTAL_KEY, total);
            set(INTERNAL_SUM_KEY, sum);
            set(INTERNAL_MIN_KEY, min);
            set(INTERNAL_MAX_KEY, max);
            set(INTERNAL_SUMSQUARE_KEY, sumsquares);
        }
    }

    public int getNumberOfFeatures() {
        int total = getTotal();
        if (total == 0) {
            return 0;
        } else {
            double[] sum = (double[]) super.get(INTERNAL_SUM_KEY);
            return sum.length;
        }
    }

    public double[] getSum() {
        int total = getTotal();
        if (total == 0) {
            return null;
        } else {
            return (double[]) super.get(INTERNAL_SUM_KEY);
        }
    }

    public double[] getSumSquares() {
        int total = getTotal();
        if (total == 0) {
            return null;
        }
        if (total == 1) {
            double[] sum = (double[]) super.get(INTERNAL_SUM_KEY);

            int features = sum.length;
            double[] sumsquares = new double[features * (features + 1) / 2];
            int count = 0;
            for (int i = 0; i < features; i++) {
                for (int j = i; j < features; j++) {
                    sumsquares[count] = sum[i] * sum[j];
                    count++;
                }
            }
            return sumsquares;
        } else {
            return (double[]) super.get(INTERNAL_SUMSQUARE_KEY);
        }
    }

    public double getProbability(double[] featArray, double[] err, boolean normalizeOnAvg) {
        double[] sum = (double[]) super.get(INTERNAL_SUM_KEY);
        double[] sumsquares = (double[]) super.get(INTERNAL_SUMSQUARE_KEY);
        MultivariateNormalDistribution mnd = MultivariateNormalDistribution.getDistribution(sum, sumsquares, getTotal(), false);
        if (mnd == null) {
            //todo handle dirac to be replaced later
            return 0;
        } else {
            return mnd.density(featArray, normalizeOnAvg);
        }
    }

    public double[] getProbabilityArray(double[][] featArray, double[] err, boolean normalizeOnAvg) {
        double[] res = new double[featArray.length];

        double[] sum = (double[]) super.get(INTERNAL_SUM_KEY);
        double[] sumsquares = (double[]) super.get(INTERNAL_SUMSQUARE_KEY);
        MultivariateNormalDistribution mnd = MultivariateNormalDistribution.getDistribution(sum, sumsquares, getTotal(), false);

        if (mnd == null) {
            //todo handle dirac to be replaced later
            return res;
        } else {
            for (int i = 0; i < res.length; i++) {
                res[i] = mnd.density(featArray[i], normalizeOnAvg);
            }
            return res;
        }

    }

    public int getTotal() {
        Integer x = (Integer) super.get(INTERNAL_TOTAL_KEY);
        if (x == null) {
            return 0;
        } else {
            return x;
        }
    }


    public double[] getAvg() {
        int total = getTotal();
        if (total == 0) {
            return null;
        }
        if (total == 1) {
            return (double[]) super.get(INTERNAL_SUM_KEY);
        } else {
            double[] avg = (double[]) super.get(INTERNAL_SUM_KEY);
            for (int i = 0; i < avg.length; i++) {
                avg[i] = avg[i] / total;
            }
            return avg;
        }

    }


    public double[] getCovarianceArray(double[] avg, double[] err) {
        if (avg == null) {
            double[] errClone = new double[err.length];
            System.arraycopy(err, 0, errClone, 0, err.length);
            return errClone;
        }
        if (err == null) {
            err = new double[avg.length];
        }
        int features = avg.length;

        int total = getTotal();
        if (total == 0) {
            return null;
        }
        if (total > 1) {
            double[] covariances = new double[features];
            double[] sumsquares = (double[]) super.get(INTERNAL_SUMSQUARE_KEY);

            double correction = total;
            correction = correction / (total - 1);

            int count = 0;
            for (int i = 0; i < features; i++) {
                covariances[i] = (sumsquares[count] / total - avg[i] * avg[i]) * correction;
                if (covariances[i] < err[i]) {
                    covariances[i] = err[i];
                }
                count += features - i;
            }
            return covariances;
        } else {
            double[] errClone = new double[err.length];
            System.arraycopy(err, 0, errClone, 0, err.length);
            return errClone;
        }
    }


    public Matrix getCovariance(double[] avg, double[] err) {
        int features = avg.length;

        int total = getTotal();
        if (total == 0) {
            return null;
        }
        if (err == null) {
            err = new double[avg.length];
        }
        if (total > 1) {
            double[] covariances = new double[features * features];
            double[] sumsquares = (double[]) super.get(INTERNAL_SUMSQUARE_KEY);

            double correction = total;
            correction = correction / (total - 1);

            int count = 0;
            for (int i = 0; i < features; i++) {
                for (int j = i; j < features; j++) {
                    covariances[i * features + j] = (sumsquares[count] / total - avg[i] * avg[j]) * correction;
                    covariances[j * features + i] = covariances[i * features + j];
                    count++;
                    if (covariances[i * features + i] < err[i]) {
                        covariances[i * features + i] = err[i];
                    }
                }
            }
            return new Matrix(covariances, features, features);
        } else {
            return null;
        }
    }

    public double[] getMin() {
        int total = getTotal();
        if (total == 0) {
            return null;
        }
        if (total == 1) {
            double[] min = (double[]) super.get(INTERNAL_SUM_KEY);
            return min;
        } else {
            double[] min = (double[]) super.get(INTERNAL_MIN_KEY);
            return min;
        }
    }

    public double[] getMax() {
        int total = getTotal();
        if (total == 0) {
            return null;
        }
        if (total == 1) {
            double[] max = (double[]) super.get(INTERNAL_SUM_KEY);
            return max;
        } else {
            double[] max = (double[]) super.get(INTERNAL_MAX_KEY);
            return max;
        }
    }

    public long[] getSubGraph() {
        long[] res = (long[]) super.get(INTERNAL_SUBGAUSSIAN_KEY);
        if (res == null) {
            res = new long[0];
        }
        return res;
    }


    public static double distance(double[] features, double[] avg, double[] precisions) {
        double max = 0;
        double temp;
        for (int i = 0; i < features.length; i++) {
            temp = (features[i] - avg[i]) * (features[i] - avg[i]) / precisions[i];
            if (temp > max) {
                max = temp;
            }
        }
        return Math.sqrt(max);
    }
}

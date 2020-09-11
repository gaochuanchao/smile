/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package smile.base.mlp;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import smile.math.MathEx;
import smile.math.matrix.Matrix;

/**
 * A layer in the neural network.
 *
 * @author Haifeng Li
 */
public abstract class Layer implements Serializable {
    private static final long serialVersionUID = 2L;
    /**
     * The number of neurons in this layer
     */
    protected int n;
    /**
     * The number of input variables.
     */
    protected int p;
    /**
     * The affine transformation matrix.
     */
    protected Matrix weight;
    /**
     * The bias.
     */
    protected double[] bias;
    /**
     * The output vector.
     */
    protected transient ThreadLocal<double[]> output;
    /**
     * The output gradient.
     */
    protected transient ThreadLocal<double[]> outputGradient;
    /**
     * The weight gradient.
     */
    protected transient ThreadLocal<Matrix> weightGradient;
    /**
     * The bias gradient.
     */
    protected transient ThreadLocal<double[]> biasGradient;
    /**
     * The weight update.
     */
    protected transient ThreadLocal<Matrix> weightUpdate;
    /**
     * The bias update.
     */
    protected transient ThreadLocal<double[]> biasUpdate;

    /**
     * Constructor. Randomly initialized weights and zero bias.
     *
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public Layer(int n, int p) {
        this(Matrix.rand(n, p, -Math.sqrt(6.0 / (n+p)), Math.sqrt(6.0 / (n+p))), new double[n]);
    }

    /**
     * Constructor.
     * @param weight the weight matrix.
     * @param bias the bias vector.
     */
    public Layer(Matrix weight, double[] bias) {
        this.n = weight.nrows();
        this.p = weight.ncols();
        this.weight = weight;
        this.bias = bias;

        init();
    }

    /**
     * Initializes the workspace when deserializing the object.
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        init();
    }

    /**
     * Initializes the workspace.
     */
    private void init() {
        output = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
        outputGradient = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
        weightGradient = new ThreadLocal<Matrix>() {
            protected synchronized Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        biasGradient = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
        weightUpdate = new ThreadLocal<Matrix>() {
            protected synchronized Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        biasUpdate = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
    }

    /** Returns the dimension of output vector. */
    public int getOutputSize() {
        return n;
    }

    /** Returns the dimension of input vector (not including bias value). */
    public int getInputSize() {
        return p;
    }

    /** Returns the output vector. */
    public double[] output() {
        return output.get();
    }

    /** Returns the output gradient vector. */
    public double[] gradient() {
        return outputGradient.get();
    }

    /**
     * Propagates signals from a lower layer to this layer.
     * @param x the lower layer signals.
     */
    public void propagate(double[] x) {
        double[] output = this.output.get();
        System.arraycopy(bias, 0, output, 0, n);
        weight.mv(1.0, x, 1.0, output);
        f(output);
    }

    /**
     * The activation or output function.
     * @param x the input and output values.
     */
    public abstract void f(double[] x);

    /**
     * Propagates the errors back to a lower layer.
     * @param lowerLayerGradient the gradient vector of lower layer.
     */
    public abstract void backpropagate(double[] lowerLayerGradient);

    /**
     * Computes the parameter gradient.
     *
     * @param x the input vector.
     * @param eta the learning rate. For mini-batch, the learning rate
     *            should be 0 so that only gradient is calculated.
     *            Otherwise, the weights are updated directly.
     * @param alpha the momentum factor.
     */
    public void computeGradient(double[] x, double eta, double alpha) {
        double[] outputGradient = this.outputGradient.get();

        if (eta > 0.0) {
            if (alpha > 0.0) {
                Matrix weightUpdate = this.weightUpdate.get();
                double[] biasUpdate = this.biasUpdate.get();

                weightUpdate.mul(alpha);
                weightUpdate.add(eta, outputGradient, x);
                weight.add(1.0, weightUpdate);
                for (int i = 0; i < n; i++) {
                    double b = alpha * biasUpdate[i] + eta * outputGradient[i];
                    biasUpdate[i] = b;
                    bias[i] += b;
                }
            } else {
                weight.add(eta, outputGradient, x);
                for (int i = 0; i < n; i++) {
                    bias[i] += eta * outputGradient[i];
                }
            }
        } else {
            Matrix weightGradient = this.weightGradient.get();
            double[] biasGradient = this.biasGradient.get();

            weightGradient.add(1.0, outputGradient, x);
            for (int i = 0; i < n; i++) {
                biasGradient[i] += outputGradient[i];
            }
        }
    }

    /**
     * Adjust network weights by back-propagation algorithm.
     * @param eta the learning rate.
     * @param alpha the momentum factor
     * @param lambda weight decay factor
     */
    public void update(double eta, double alpha, double lambda) {
        Matrix weightGradient = this.weightGradient.get();
        double[] biasGradient = this.biasGradient.get();

        if (alpha > 0.0) {
            Matrix weightUpdate = this.weightUpdate.get();
            double[] biasUpdate = this.biasUpdate.get();

            weightUpdate.add(alpha, eta, weightGradient);
            for (int i = 0; i < n; i++) {
                biasUpdate[i] = alpha * biasUpdate[i] + eta * biasGradient[i];
            }

            weight.add(1.0, weightUpdate);
            MathEx.add(bias, biasUpdate);
        } else {
            weight.add(eta, weightGradient);
            for (int i = 0; i < n; i++) {
                bias[i] += eta * biasGradient[i];
            }
        }

        // Weight decay as the weights are multiplied
        // by a factor slightly less than 1. This prevents the weights
        // from growing too large, and can be seen as gradient descent
        // on a quadratic regularization term.
        if (lambda < 1.0) {
            weight.mul(lambda);
        }

        weightGradient.fill(0.0);
        Arrays.fill(biasGradient, 0.0);
    }

    /**
     * Returns a hidden layer with linear activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder linear(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.linear());
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder rectifier(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.rectifier());
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder sigmoid(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.sigmoid());
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder tanh(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.tanh());
    }

    /**
     * Returns an output layer with mean squared error cost function.
     * @param n the number of neurons.
     * @param f the output function.
     */
    public static OutputLayerBuilder mse(int n, OutputFunction f) {
        return new OutputLayerBuilder(n, f, Cost.MEAN_SQUARED_ERROR);
    }

    /**
     * Returns an output layer with (log-)likelihood cost function.
     * @param n the number of neurons.
     * @param f the output function.
     */
    public static OutputLayerBuilder mle(int n, OutputFunction f) {
        return new OutputLayerBuilder(n, f, Cost.LIKELIHOOD);
    }
}
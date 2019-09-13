/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package software.amazon.ai.training.optimizer;

import java.util.ArrayList;
import java.util.List;
import software.amazon.ai.ndarray.NDArray;
import software.amazon.ai.ndarray.NDList;
import software.amazon.ai.ndarray.internal.NDArrayEx;
import software.amazon.ai.nn.Parameter;
import software.amazon.ai.training.optimizer.learningrate.LearningRateTracker;
import software.amazon.ai.util.PairList;

/** An NAG optimizer. Build with {@link Nag.Builder}. */
public class Nag extends Optimizer {

    private LearningRateTracker learningRateTracker;
    private float momentum;
    private List<NDArray> momentumStates;

    protected Nag(Builder builder) {
        super(builder);
        learningRateTracker = builder.getLearningRateTracker();
        momentum = builder.getMomentum();
    }

    @Override
    protected boolean initializeStates(PairList<String, Parameter> parameters) {
        if (momentum != 0f) {
            momentumStates = new ArrayList<>(parameters.size());
            for (Parameter param : parameters.values()) {
                momentumStates.add(param.getArray().zerosLike());
            }
        }
        return true;
    }

    // TODO: make this protected after integrate with PS store
    @Override
    public void update(int index, NDArray weight, NDArray grad) {
        // TODO: Support Mixed precision Sparse
        float newLearningRate = learningRateTracker.getNewLearningRate(updateCount(index));
        float weightDecay = getWeightDecay(index);
        NDList inputs;
        if (momentum != 0f) {
            inputs = new NDList(weight, grad, momentumStates.get(index));
        } else {
            inputs = new NDList(weight, grad);
        }
        NDList weights = new NDList(weight);

        NDArrayEx ex = weight.getNDArrayInternal();
        ex.nagUpdate(
                inputs, weights, newLearningRate, weightDecay, rescaleGrad, clipGrad, momentum);
    }

    public static final class Builder extends BaseBuilder<Builder> {

        private LearningRateTracker learningRateTracker;
        private float momentum;

        public Builder setLearningRateTracker(LearningRateTracker learningRateTracker) {
            this.learningRateTracker = learningRateTracker;
            return this;
        }

        public Builder setMomentum(float momentum) {
            this.momentum = momentum;
            return this;
        }

        public LearningRateTracker getLearningRateTracker() {
            return learningRateTracker;
        }

        public float getMomentum() {
            return momentum;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Nag build() {
            if (learningRateTracker == null) {
                throw new IllegalArgumentException("No lrTracker set");
            }
            if (momentum == 0) {
                throw new IllegalArgumentException("The momentum should be set");
            }
            return new Nag(this);
        }
    }
}

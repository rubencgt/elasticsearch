/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.Scope;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.WhileNode;
import org.elasticsearch.painless.symbol.ScriptRoot;

import java.util.Objects;

/**
 * Represents a while loop.
 */
public final class SWhile extends AStatement {

    private AExpression condition;
    private final SBlock block;

    private boolean continuous = false;

    public SWhile(Location location, AExpression condition, SBlock block) {
        super(location);

        this.condition = Objects.requireNonNull(condition);
        this.block = block;
    }

    @Override
    Output analyze(ScriptRoot scriptRoot, Scope scope, Input input) {
        this.input = input;
        output = new Output();

        scope = scope.newLocalScope();

        AExpression.Input conditionInput = new AExpression.Input();
        conditionInput.expected = boolean.class;
        condition.analyze(scriptRoot, scope, conditionInput);
        condition.cast();

        if (condition instanceof EBoolean) {
            continuous = ((EBoolean)condition).constant;

            if (!continuous) {
                throw createError(new IllegalArgumentException("Extraneous while loop."));
            }

            if (block == null) {
                throw createError(new IllegalArgumentException("While loop has no escape."));
            }
        }

        if (block != null) {
            Input blockInput = new Input();
            blockInput.beginLoop = true;
            blockInput.inLoop = true;

            Output blockOutput = block.analyze(scriptRoot, scope, blockInput);

            if (blockOutput.loopEscape && blockOutput.anyContinue == false) {
                throw createError(new IllegalArgumentException("Extraneous while loop."));
            }

            if (continuous && blockOutput.anyBreak == false) {
                output.methodEscape = true;
                output.allEscape = true;
            }

            blockOutput.statementCount = Math.max(1, blockOutput.statementCount);
        }

        output.statementCount = 1;

        return output;
    }

    @Override
    WhileNode write(ClassNode classNode) {
        WhileNode whileNode = new WhileNode();

        whileNode.setConditionNode(condition.cast(condition.write(classNode)));
        whileNode.setBlockNode(block == null ? null : block.write(classNode));

        whileNode.setLocation(location);
        whileNode.setContinuous(continuous);

        return whileNode;
    }

    @Override
    public String toString() {
        return singleLineToString(condition, block);
    }
}

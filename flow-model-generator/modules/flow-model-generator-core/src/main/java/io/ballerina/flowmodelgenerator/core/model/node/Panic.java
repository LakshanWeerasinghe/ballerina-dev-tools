/*
 *  Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.flowmodelgenerator.core.model.node;

import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.flowmodelgenerator.core.model.Expression;
import io.ballerina.flowmodelgenerator.core.model.FlowNode;

import java.util.Map;

/**
 * Represents the properties of a panic node.
 *
 * @since 1.4.0
 */
public class Panic extends FlowNode {

    public static final String LABEL = "Panic";
    public static final String DESCRIPTION = "Panic and stop the execution";
    public static final String PANIC_EXPRESSION_DOC = "Panic value";

    @Override
    protected void setConstData() {
        this.label = LABEL;
        this.kind = Kind.PANIC;
        this.description = DESCRIPTION;
    }

    @Override
    public String toSource() {
        SourceBuilder sourceBuilder = new SourceBuilder();

        sourceBuilder.keyword(SyntaxKind.PANIC_KEYWORD);
        Expression expression = getProperty(Expression.EXPRESSION_KEY);
        if (expression != null) {
            sourceBuilder
                    .whiteSpace()
                    .expression(expression);
        }
        sourceBuilder.endOfStatement();
        return sourceBuilder.build(false);
    }

    @Override
    public void setTemplateData() {
        this.nodeProperties = Map.of(Expression.EXPRESSION_KEY, Expression.getDefaultExpression(PANIC_EXPRESSION_DOC));
    }
}

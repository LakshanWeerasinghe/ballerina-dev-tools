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

package io.ballerina.flowmodelgenerator.core;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.MethodSymbol;
import io.ballerina.compiler.api.symbols.ResourceMethodSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.syntax.tree.ActionNode;
import io.ballerina.compiler.syntax.tree.AssignmentStatementNode;
import io.ballerina.compiler.syntax.tree.BlockStatementNode;
import io.ballerina.compiler.syntax.tree.BreakStatementNode;
import io.ballerina.compiler.syntax.tree.CheckExpressionNode;
import io.ballerina.compiler.syntax.tree.ClientResourceAccessActionNode;
import io.ballerina.compiler.syntax.tree.CompoundAssignmentStatementNode;
import io.ballerina.compiler.syntax.tree.ContinueStatementNode;
import io.ballerina.compiler.syntax.tree.DoStatementNode;
import io.ballerina.compiler.syntax.tree.ElseBlockNode;
import io.ballerina.compiler.syntax.tree.ExplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionStatementNode;
import io.ballerina.compiler.syntax.tree.FailStatementNode;
import io.ballerina.compiler.syntax.tree.ForEachStatementNode;
import io.ballerina.compiler.syntax.tree.ForkStatementNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IfElseStatementNode;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.LocalTypeDefinitionStatementNode;
import io.ballerina.compiler.syntax.tree.LockStatementNode;
import io.ballerina.compiler.syntax.tree.MatchStatementNode;
import io.ballerina.compiler.syntax.tree.NewExpressionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.PanicStatementNode;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.RemoteMethodCallActionNode;
import io.ballerina.compiler.syntax.tree.RetryStatementNode;
import io.ballerina.compiler.syntax.tree.ReturnStatementNode;
import io.ballerina.compiler.syntax.tree.RollbackStatementNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.StatementNode;
import io.ballerina.compiler.syntax.tree.TransactionStatementNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.WhileStatementNode;
import io.ballerina.flowmodelgenerator.core.model.Branch;
import io.ballerina.flowmodelgenerator.core.model.FlowNode;
import io.ballerina.flowmodelgenerator.core.model.properties.Client;
import io.ballerina.flowmodelgenerator.core.model.properties.DefaultExpression;
import io.ballerina.flowmodelgenerator.core.model.properties.HttpApiEvent;
import io.ballerina.flowmodelgenerator.core.model.properties.HttpGet;
import io.ballerina.flowmodelgenerator.core.model.properties.HttpPost;
import io.ballerina.flowmodelgenerator.core.model.properties.IfNode;
import io.ballerina.flowmodelgenerator.core.model.properties.Return;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

/**
 * Analyzes the source code and generates the flow model.
 *
 * @since 2201.9.0
 */
class CodeAnalyzer extends NodeVisitor {

    private final List<FlowNode> flowNodeList;
    private final List<Client> clients;
    private FlowNode.NodeBuilder nodeBuilder;
    private final Client.Builder clientBuilder;
    private final SemanticModel semanticModel;
    private final Stack<FlowNode.NodeBuilder> flowNodeBuilderStack;
    private TypedBindingPatternNode typedBindingPatternNode;

    public CodeAnalyzer(SemanticModel semanticModel) {
        this.flowNodeList = new ArrayList<>();
        this.clientBuilder = new Client.Builder();
        this.nodeBuilder = new FlowNode.NodeBuilder(semanticModel);
        this.semanticModel = semanticModel;
        this.flowNodeBuilderStack = new Stack<>();
        this.clients = new ArrayList<>();
    }

    @Override
    public void visit(VariableDeclarationNode variableDeclarationNode) {
        Optional<ExpressionNode> initializer = variableDeclarationNode.initializer();
        if (initializer.isEmpty()) {
            return;
        }
        if (variableDeclarationNode.finalKeyword().isPresent()) {
            this.nodeBuilder.addFlag(FlowNode.NODE_FLAG_FINAL);
        }
        ExpressionNode initializerNode = initializer.get();
        this.typedBindingPatternNode = variableDeclarationNode.typedBindingPattern();
        initializerNode.accept(this);

        // Generate the default expression node if a node is not built
        if (this.nodeBuilder.isDefault()) {
            this.nodeBuilder.setLineRange(variableDeclarationNode);
            DefaultExpression.Builder defaultExpressionBuilder = new DefaultExpression.Builder(semanticModel);
            defaultExpressionBuilder.setExpression(initializerNode);
            defaultExpressionBuilder.setVariable(this.typedBindingPatternNode);
            this.nodeBuilder.setPropertiesBuilder(defaultExpressionBuilder);
        }

        appendNode();
        this.typedBindingPatternNode = null;
    }

    @Override
    public void visit(FunctionDefinitionNode functionDefinitionNode) {
        Optional<Symbol> symbol = semanticModel.symbol(functionDefinitionNode);
        if (symbol.isEmpty()) {
            return;
        }

        switch (symbol.get().kind()) {
            case RESOURCE_METHOD -> {
                HttpApiEvent.Builder httpApiEventBuilder = new HttpApiEvent.Builder(semanticModel);
                httpApiEventBuilder.setSymbol((ResourceMethodSymbol) symbol.get());
                this.nodeBuilder.addFlag(FlowNode.NODE_FLAG_RESOURCE);
                this.nodeBuilder.setPropertiesBuilder(httpApiEventBuilder);
            }
            default -> {
            }
        }
        this.nodeBuilder.setLineRange(functionDefinitionNode);

        appendNode();
        super.visit(functionDefinitionNode);
    }

    @Override
    public void visit(ReturnStatementNode returnStatementNode) {
        Optional<ExpressionNode> expression = returnStatementNode.expression();
        expression.ifPresent(expressionNode -> expressionNode.accept(this));
        if (this.nodeBuilder.isDefault()) {
            Return.Builder returnBuilder = new Return.Builder(semanticModel);
            this.nodeBuilder.setLineRange(returnStatementNode);
            expression.ifPresent(returnBuilder::setExpressionNode);
            this.nodeBuilder.setPropertiesBuilder(returnBuilder);
        }
        this.nodeBuilder.setReturning();
        appendNode();
    }

    @Override
    public void visit(RemoteMethodCallActionNode remoteMethodCallActionNode) {
        String methodName = remoteMethodCallActionNode.methodName().name().text();
        ExpressionNode expression = remoteMethodCallActionNode.expression();
        SeparatedNodeList<FunctionArgumentNode> argumentNodes = remoteMethodCallActionNode.arguments();
        handleActionNode(remoteMethodCallActionNode, methodName, expression, argumentNodes, null);
    }

    @Override
    public void visit(ClientResourceAccessActionNode clientResourceAccessActionNode) {
        String methodName = clientResourceAccessActionNode.methodName()
                .map(simpleNameReference -> simpleNameReference.name().text()).orElse("");
        ExpressionNode expression = clientResourceAccessActionNode.expression();
        SeparatedNodeList<FunctionArgumentNode> functionArgumentNodes =
                clientResourceAccessActionNode.arguments().map(ParenthesizedArgList::arguments).orElse(null);

        handleActionNode(clientResourceAccessActionNode, methodName, expression, functionArgumentNodes,
                clientResourceAccessActionNode.resourceAccessPath());
    }

    private void handleActionNode(ActionNode actionNode, String methodName, ExpressionNode expressionNode,
                                  SeparatedNodeList<FunctionArgumentNode> argumentNodes,
                                  SeparatedNodeList<Node> resourceAccessPathNodes) {
        NonTerminalNode statementNode = CommonUtils.getExpressionWithCheck(actionNode);
        Optional<Symbol> symbol = semanticModel.symbol(actionNode);
        if (symbol.isEmpty() || (symbol.get().kind() != SymbolKind.METHOD &&
                symbol.get().kind() != SymbolKind.RESOURCE_METHOD)) {
            return;
        }

        MethodSymbol methodSymbol = (MethodSymbol) symbol.get();
        String moduleName = symbol.get().getModule().flatMap(Symbol::getName).orElse("");

        switch (moduleName) {
            case "http" -> {
                switch (methodName) {
                    case "get" -> {
                        HttpGet.Builder httpGetBuilder = new HttpGet.Builder(semanticModel);
                        httpGetBuilder.addClient(expressionNode);
                        httpGetBuilder.addTargetTypeValue(statementNode);
                        httpGetBuilder.addFunctionArguments(argumentNodes);
                        httpGetBuilder.addResourceAccessPath(resourceAccessPathNodes);
                        httpGetBuilder.setVariable(this.typedBindingPatternNode);
                        methodSymbol.typeDescriptor().params().ifPresent(httpGetBuilder::addHttpParameters);
                        this.nodeBuilder.setPropertiesBuilder(httpGetBuilder);
                    }
                    case "post" -> {
                        HttpPost.Builder httpPostBuilder = new HttpPost.Builder(semanticModel);
                        httpPostBuilder.addClient(expressionNode);
                        httpPostBuilder.addTargetTypeValue(statementNode);
                        httpPostBuilder.addFunctionArguments(argumentNodes);
                        httpPostBuilder.addResourceAccessPath(resourceAccessPathNodes);
                        httpPostBuilder.setVariable(this.typedBindingPatternNode);
                        methodSymbol.typeDescriptor().params().ifPresent(httpPostBuilder::addHttpParameters);
                        this.nodeBuilder.setPropertiesBuilder(httpPostBuilder);
                    }
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
        this.nodeBuilder.setLineRange(statementNode);
    }

    @Override
    public void visit(IfElseStatementNode ifElseStatementNode) {
        this.nodeBuilder.setLineRange(ifElseStatementNode);
        IfNode.Builder ifNodeBuilder = new IfNode.Builder(semanticModel);
        ifNodeBuilder.setConditionExpression(ifElseStatementNode.condition());

        BlockStatementNode ifBody = ifElseStatementNode.ifBody();
        List<FlowNode> ifNodes = new ArrayList<>();
        startBranch();
        for (StatementNode statement : ifBody.statements()) {
            statement.accept(this);
            ifNodes.add(buildNode());
        }
        endBranch();
        this.nodeBuilder.addBranch(IfNode.IF_THEN_LABEL, Branch.BranchKind.BLOCK, ifNodes);

        Optional<Node> elseBody = ifElseStatementNode.elseBody();
        if (elseBody.isPresent()) {
            startBranch();
            List<FlowNode> elseBodyChildNodes = analyzeElseBody(elseBody.get());
            endBranch();
            this.nodeBuilder.addBranch(IfNode.IF_ELSE_LABEL, Branch.BranchKind.BLOCK, elseBodyChildNodes);
        }

        this.nodeBuilder.setPropertiesBuilder(ifNodeBuilder);
        appendNode();
    }

    private List<FlowNode> analyzeElseBody(Node elseBody) {
        return switch (elseBody.kind()) {
            case ELSE_BLOCK -> analyzeElseBody(((ElseBlockNode) elseBody).elseBody());
            case BLOCK_STATEMENT -> {
                List<FlowNode> elseNodes = new ArrayList<>();
                for (StatementNode statement : ((BlockStatementNode) elseBody).statements()) {
                    statement.accept(this);
                    elseNodes.add(buildNode());
                }
                yield elseNodes;
            }
            case IF_ELSE_STATEMENT -> {
                elseBody.accept(this);
                yield List.of(buildNode());
            }
            default -> new ArrayList<>();
        };
    }

    @Override
    public void visit(ImplicitNewExpressionNode implicitNewExpressionNode) {
        checkForPossibleClient(implicitNewExpressionNode);
        super.visit(implicitNewExpressionNode);
    }

    @Override
    public void visit(ExplicitNewExpressionNode explicitNewExpressionNode) {
        checkForPossibleClient(explicitNewExpressionNode);
        super.visit(explicitNewExpressionNode);
    }

    private void checkForPossibleClient(NewExpressionNode newExpressionNode) {
        this.clientBuilder.setTypedBindingPattern(this.typedBindingPatternNode);
        semanticModel.typeOf(CommonUtils.getExpressionWithCheck(newExpressionNode))
                .flatMap(symbol -> CommonUtils.buildClient(this.clientBuilder, symbol, Client.ClientScope.LOCAL))
                .ifPresent(clients::add);
    }

    @Override
    public void visit(AssignmentStatementNode assignmentStatementNode) {
        handleDefaultStatementNode(assignmentStatementNode, () -> super.visit(assignmentStatementNode));
    }

    @Override
    public void visit(CompoundAssignmentStatementNode compoundAssignmentStatementNode) {
        handleDefaultStatementNode(compoundAssignmentStatementNode, () -> super.visit(compoundAssignmentStatementNode));
    }

    @Override
    public void visit(BlockStatementNode blockStatementNode) {
        handleDefaultStatementNode(blockStatementNode, () -> super.visit(blockStatementNode));
    }

    @Override
    public void visit(BreakStatementNode breakStatementNode) {
        handleDefaultStatementNode(breakStatementNode, () -> super.visit(breakStatementNode));
    }

    @Override
    public void visit(FailStatementNode failStatementNode) {
        handleDefaultStatementNode(failStatementNode, () -> super.visit(failStatementNode));
    }

    @Override
    public void visit(ExpressionStatementNode expressionStatementNode) {
        handleDefaultStatementNode(expressionStatementNode, () -> super.visit(expressionStatementNode));
    }

    @Override
    public void visit(ContinueStatementNode continueStatementNode) {
        handleDefaultStatementNode(continueStatementNode, () -> super.visit(continueStatementNode));
    }

    @Override
    public void visit(WhileStatementNode whileStatementNode) {
        handleDefaultStatementNode(whileStatementNode, () -> super.visit(whileStatementNode));
    }

    @Override
    public void visit(PanicStatementNode panicStatementNode) {
        handleDefaultStatementNode(panicStatementNode, () -> super.visit(panicStatementNode));
    }

    @Override
    public void visit(LocalTypeDefinitionStatementNode localTypeDefinitionStatementNode) {
        handleDefaultStatementNode(localTypeDefinitionStatementNode,
                () -> super.visit(localTypeDefinitionStatementNode));
    }

    @Override
    public void visit(LockStatementNode lockStatementNode) {
        handleDefaultStatementNode(lockStatementNode, () -> super.visit(lockStatementNode));
    }

    @Override
    public void visit(ForkStatementNode forkStatementNode) {
        handleDefaultStatementNode(forkStatementNode, () -> super.visit(forkStatementNode));
    }

    @Override
    public void visit(TransactionStatementNode transactionStatementNode) {
        handleDefaultStatementNode(transactionStatementNode, () -> super.visit(transactionStatementNode));
    }

    @Override
    public void visit(ForEachStatementNode forEachStatementNode) {
        handleDefaultStatementNode(forEachStatementNode, () -> super.visit(forEachStatementNode));
    }

    @Override
    public void visit(RollbackStatementNode rollbackStatementNode) {
        handleDefaultStatementNode(rollbackStatementNode, () -> super.visit(rollbackStatementNode));
    }

    @Override
    public void visit(RetryStatementNode retryStatementNode) {
        handleDefaultStatementNode(retryStatementNode, () -> super.visit(retryStatementNode));
    }

    @Override
    public void visit(MatchStatementNode matchStatementNode) {
        handleDefaultStatementNode(matchStatementNode, () -> super.visit(matchStatementNode));
    }

    @Override
    public void visit(DoStatementNode doStatementNode) {
        handleDefaultStatementNode(doStatementNode, () -> super.visit(doStatementNode));
    }

    @Override
    public void visit(CheckExpressionNode checkExpressionNode) {
        switch (checkExpressionNode.checkKeyword().text()) {
            case Constants.CHECK -> this.nodeBuilder.addFlag(FlowNode.NODE_FLAG_CHECKED);
            case Constants.CHECKPANIC -> this.nodeBuilder.addFlag(FlowNode.NODE_FLAG_CHECKPANIC);
            default -> {
            }
        }
        checkExpressionNode.expression().accept(this);
    }

    // Utility methods

    private void appendNode() {
        if (this.flowNodeBuilderStack.isEmpty()) {
            this.flowNodeList.add(buildNode());
        }
    }

    private void startBranch() {
        this.flowNodeBuilderStack.push(this.nodeBuilder);
        this.nodeBuilder = new FlowNode.NodeBuilder(semanticModel);
    }

    private void endBranch() {
        this.nodeBuilder = this.flowNodeBuilderStack.pop();
    }

    private FlowNode buildNode() {
        FlowNode flowNode = this.nodeBuilder.build();
        this.nodeBuilder = new FlowNode.NodeBuilder(semanticModel);
        return flowNode;
    }

    private void handleDefaultStatementNode(NonTerminalNode statementNode, Runnable runnable) {
        this.nodeBuilder.setLineRange(statementNode);
        runnable.run();
        appendNode();
    }

    public List<FlowNode> getFlowNodes() {
        return flowNodeList;
    }

    public List<Client> getClients() {
        return clients;
    }
}

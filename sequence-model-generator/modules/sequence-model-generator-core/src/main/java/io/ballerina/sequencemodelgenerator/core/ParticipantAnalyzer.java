package io.ballerina.sequencemodelgenerator.core;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.ExpressionFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionCallExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.NameReferenceNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.RemoteMethodCallActionNode;
import io.ballerina.compiler.syntax.tree.ReturnStatementNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.sequencemodelgenerator.core.model.Expression;
import io.ballerina.sequencemodelgenerator.core.model.Interaction;
import io.ballerina.sequencemodelgenerator.core.model.Participant;
import io.ballerina.sequencemodelgenerator.core.model.SequenceNode;
import io.ballerina.tools.text.LineRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

public class ParticipantAnalyzer extends NodeVisitor {

    private final List<SequenceNode> sequenceNodes;
    private final SemanticModel semanticModel;
    private final Stack<SequenceNode.Builder> nodeBuilderStack;
    private final String moduleName;

    private String participantId;
    private SequenceNode.Builder nodeBuilder;
    private TypedBindingPatternNode typedBindingPatternNode;
    private Participant participant;
    private List<String> dependentParticipants;

    public ParticipantAnalyzer(SemanticModel semanticModel, String moduleName) {
        this.semanticModel = semanticModel;
        this.sequenceNodes = new ArrayList<>();
        this.nodeBuilderStack = new Stack<>();
        this.moduleName = moduleName;

        this.nodeBuilder = new SequenceNode.Builder(semanticModel);
    }

    @Override
    public void visit(FunctionDefinitionNode functionDefinitionNode) {
        Participant.Builder participantBuilder = new Participant.Builder()
                .name(functionDefinitionNode.functionName().text())
                .kind(Participant.ParticipantKind.FUNCTION)
                .moduleName(moduleName);
        generateParticipantId(functionDefinitionNode, participantBuilder);

        functionDefinitionNode.functionBody().accept(this);
        participantBuilder.nodes(sequenceNodes);

        participant = participantBuilder.build();
    }

    @Override
    public void visit(VariableDeclarationNode variableDeclarationNode) {
        this.typedBindingPatternNode = variableDeclarationNode.typedBindingPattern();
        variableDeclarationNode.initializer().ifPresent(expressionNode -> expressionNode.accept(this));
    }

    @Override
    public void visit(FunctionCallExpressionNode functionCallExpressionNode) {
        NameReferenceNode functionName = functionCallExpressionNode.functionName();

        String targetId = ParticipantManager.getInstance().getParticipantId(functionName);
        SequenceNode.Builder interactionBuilder = new Interaction.Builder(semanticModel)
                .interactionType(Interaction.InteractionType.FUNCTION_CALL)
                .targetId(targetId)
                .location(functionCallExpressionNode);

        List<ExpressionNode> paramList = functionCallExpressionNode.arguments().stream()
                .map(argument -> ExpressionNode.Factory.create(semanticModel, argument))
                .toList();

        interactionBuilder
                .property(Interaction.PARAMS_LABEL, paramList)
                .property(Interaction.NAME_LABEL, Expression.Factory.createStringType(functionName))
                .property(Interaction.VALUE_LABEL, Expression.Factory.create(semanticModel,
                        functionCallExpressionNode, typedBindingPatternNode.bindingPattern()));

        appendNode(interactionBuilder);
    }

    @Override
    public void visit(ReturnStatementNode returnStatementNode) {
        returnStatementNode.expression().ifPresent(this::handleReturnInteraction);
    }

    @Override
    public void visit(ExpressionFunctionBodyNode expressionFunctionBodyNode) {
        handleReturnInteraction(expressionFunctionBodyNode.expression());
    }

    // Handle methods
    private void handleReturnInteraction(ExpressionNode expressionNode) {
        SequenceNode.Builder builder = new SequenceNode.Builder(semanticModel)
                .kind(SequenceNode.NodeKind.RETURN)
                .location(expressionNode)
                .property(Interaction.VALUE_LABEL,
                        Expression.Factory.createType(semanticModel, expressionNode));

        appendNode(interactionBuilder);
    }

    // Utility method
    public Participant getParticipant() {
        return participant;
    }

    private void appendNode(SequenceNode.Builder builder) {
        if (this.nodeBuilderStack.isEmpty()) {
            this.sequenceNodes.add(builder.build());
        }
    }

    private void startBranch() {
        this.nodeBuilderStack.push(nodeBuilder);
        nodeBuilder = new SequenceNode.Builder(semanticModel);
    }

    private void endBranch() {
        nodeBuilder = this.nodeBuilderStack.pop();
    }

    private SequenceNode buildNode() {
        SequenceNode sequenceNode = nodeBuilder.build();
        nodeBuilder = new SequenceNode.Builder(semanticModel);
        return sequenceNode;
    }

    private void generateParticipantId(Node participantNode, Participant.Builder participantBuilder) {
        LineRange location = participantNode.lineRange();
        String id = String.valueOf(Objects.hash(location));
        this.participantId = id;
        participantBuilder.id(id).location(location);
    }
}

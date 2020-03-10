package demo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.model.visitors.AbstractVisitor;

public class ScoreDistributionGenerator extends AbstractVisitor {

	@Override
	public VisitorAction visit(TreeModel treeModel){
		MiningFunction miningFunction = treeModel.getMiningFunction();

		if(!(MiningFunction.CLASSIFICATION).equals(miningFunction)){
			return VisitorAction.SKIP;
		}

		return super.visit(treeModel);
	}

	/**
	 * Going down the tree data structure.
	 */
	@Override
	public void pushParent(PMMLObject parent){
		super.pushParent(parent);
	}

	/**
	 * Coming up from the tree data structure.
	 * In this point, it is guaranteed that all the child nodes have proper ScoreDistribution child elements.
	 * The record count for the current node (ie. the one that is being "popped")
	 * is computed by summing the record counts of its child nodes.
	 */
	@Override
	public PMMLObject popParent(){
		PMMLObject parent = super.popParent();

		if(parent instanceof Node){
			Node node = (Node)parent;

			processNode(node);
		}

		return parent;
	}

	private void processNode(Node node){

		if(node.hasScoreDistributions()){
			return;
		} // End if

		if(node.hasNodes()){
			Map<Object, List<Number>> valueRecordCounts = node.getNodes().stream()
				.map(Node::getScoreDistributions)
				.flatMap(x -> x.stream())
				.collect(Collectors.groupingBy(ScoreDistribution::getValue, Collectors.mapping(ScoreDistribution::getRecordCount, Collectors.toList())));

			double sumOfRecordCounts = 0d;

			Collection<Map.Entry<Object, List<Number>>> entries = valueRecordCounts.entrySet();
			for(Map.Entry<Object, List<Number>> entry : entries){
				Object value = entry.getKey();
				Double recordCount = entry.getValue().stream()
					.collect(Collectors.summingDouble(Number::doubleValue));

				sumOfRecordCounts += recordCount;

				node.addScoreDistributions(new ScoreDistribution(value, recordCount));
			}

			node.setRecordCount(sumOfRecordCounts);
		}
	}
}
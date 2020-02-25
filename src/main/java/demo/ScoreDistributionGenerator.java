package demo;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.model.visitors.AbstractVisitor;

public class ScoreDistributionGenerator extends AbstractVisitor {

	@Override
	public VisitorAction visit(TreeModel treeModel){
		MiningFunction miningFunction = treeModel.getMiningFunction();

		if(!(MiningFunction.CLASSIFICATION).equals(miningFunction)){
			throw new UnsupportedElementException(treeModel);
		}

		return super.visit(treeModel);
	}

	@Override
	public void pushParent(PMMLObject parent){
		super.pushParent(parent);
	}

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
			Map<Object, Number> recordCounts = new LinkedHashMap<>();

			List<Node> children = node.getNodes();
			for(Node child : children){
				List<ScoreDistribution> scoreDistributions = child.getScoreDistributions();
				for(ScoreDistribution scoreDistribution : scoreDistributions){
					Object childValue = scoreDistribution.getValue();
					Number childRecordCount = scoreDistribution.getRecordCount();

					Number recordCount = recordCounts.get(childValue);
					if(recordCount == null){
						recordCount = childRecordCount;
					} else

					{
						recordCount = recordCount.doubleValue() + childRecordCount.doubleValue();
					}

					recordCounts.put(childValue, recordCount);
				}
			}

			Collection<Map.Entry<Object, Number>> entries = recordCounts.entrySet();
			for(Map.Entry<Object, Number> entry : entries){
				node.addScoreDistributions(new ScoreDistribution(entry.getKey(), entry.getValue()));
			}
		}
	}
}
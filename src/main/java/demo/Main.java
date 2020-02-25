package demo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.adapters.NodeAdapter;
import org.dmg.pmml.tree.ComplexNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.NodeTransformer;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.mining.HasSegmentation;
import org.jpmml.evaluator.mining.SegmentResult;
import org.jpmml.evaluator.tree.HasDecisionPath;
import org.jpmml.evaluator.visitors.DefaultVisitorBattery;
import org.jpmml.model.VisitorBattery;

public class Main {

	static
	public void main(String... args) throws Exception {
		File pmmlFile = new File(args[0]);
		File csvFile = new File(args[1]);

		NodeTransformer nodeTransformer = new NodeTransformer(){

			@Override
			public Node fromComplexNode(ComplexNode complexNode){
				return complexNode;
			}

			@Override
			public ComplexNode toComplexNode(Node node){
				return (ComplexNode)node;
			}
		};

		NodeAdapter.NODE_TRANSFORMER_PROVIDER.set(nodeTransformer);

		VisitorBattery visitors = new VisitorBattery();
		visitors.add(ScoreDistributionGenerator.class);
		visitors.addAll(new DefaultVisitorBattery());

		Evaluator evaluator = new LoadingModelEvaluatorBuilder()
			.setLocatable(false)
			.setVisitors(visitors)
			//.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS)
			.load(pmmlFile)
			.build();

		NodeAdapter.NODE_TRANSFORMER_PROVIDER.remove();

		// Perforing the self-check
		evaluator.verify();

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile)));

		String[] header = (reader.readLine()).split(",");
		String[] line = (reader.readLine()).split(",");

		reader.close();

		Map<FieldName, Object> arguments = new LinkedHashMap<>();

		for(int i = 0; i < header.length; i++){
			arguments.put(FieldName.create(header[i]), line[i]);
		}

		Map<FieldName, ?> results = evaluator.evaluate(arguments);

		Object targetValue = results.get(FieldName.create("y"));
		System.out.println(targetValue);

		HasSegmentation hasSegmentation = (HasSegmentation)targetValue;

		Collection<? extends SegmentResult> segmentResults = hasSegmentation.getSegmentResults();
		System.out.println(segmentResults.size());

		for(SegmentResult segmentResult : segmentResults){
			processSegment(segmentResult);
		}
	}

	static
	private void processSegment(SegmentResult segmentResult){
		Object targetValue = segmentResult.getTargetValue();
		System.out.println(targetValue);

		HasDecisionPath hasDecisionPath = (HasDecisionPath)targetValue;

		String indent = "";

		DecimalFormat probabilityFormat = new DecimalFormat("0.000");

		List<Node> nodes = hasDecisionPath.getDecisionPath();
		for(Node node : nodes){
			System.out.println(indent + node);

			Predicate predicate = node.getPredicate();
			if(predicate instanceof SimplePredicate){
				SimplePredicate simplePredicate = (SimplePredicate)node.getPredicate();
				System.out.println(indent + simplePredicate.getField() + " " + simplePredicate.getOperator() + " " + simplePredicate.getValue());
			} else

			{
				System.out.println(indent + predicate);
			}

			if(node.hasScoreDistributions()){
				List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

				double sumOfRecordCounts = 0d;

				for(ScoreDistribution scoreDistribution : scoreDistributions){
					Number recordCount = scoreDistribution.getRecordCount();

					sumOfRecordCounts += recordCount.doubleValue();
				}

				for(ScoreDistribution scoreDistribution : scoreDistributions){
					Number recordCount = scoreDistribution.getRecordCount();

					System.out.println(indent + scoreDistribution.getValue() + " -> " + recordCount + ", p=" + probabilityFormat.format(recordCount.doubleValue() / sumOfRecordCounts));
				}
			}

			indent += "\t";
		}
	}
}
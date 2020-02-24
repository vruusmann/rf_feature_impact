package demo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.tree.Node;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.mining.HasSegmentation;
import org.jpmml.evaluator.mining.SegmentResult;
import org.jpmml.evaluator.tree.HasDecisionPath;
import org.jpmml.evaluator.visitors.DefaultVisitorBattery;

public class Main {

	static
	public void main(String... args) throws Exception {
		File pmmlFile = new File(args[0]);
		File csvFile = new File(args[1]);

		Evaluator evaluator = new LoadingModelEvaluatorBuilder()
			.setLocatable(false)
			.setVisitors(new DefaultVisitorBattery())
			//.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS)
			.load(pmmlFile)
			.build();

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
				for(ScoreDistribution scoreDistribution : scoreDistributions){
					System.out.println(indent + scoreDistribution.getValue() + " -> " + scoreDistribution.getRecordCount());
				}
			}

			indent += "\t";
		}
	}
}
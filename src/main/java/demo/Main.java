package demo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.adapters.NodeAdapter;
import org.dmg.pmml.tree.ComplexNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.NodeTransformer;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.TargetField;
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
		String targetClass = args[2];

		Evaluator evaluator = loadEvaluator(pmmlFile);

		// Supervised learning models are expected to have exactly one target field (aka label)
		TargetField targetField = Iterables.getOnlyElement(evaluator.getTargetFields());

		List<Map<FieldName, ?>> arguments = loadArguments(csvFile);

		System.out.println("Id" + CSV_SEPARATOR + "Tree" + CSV_SEPARATOR + "Weight" + CSV_SEPARATOR + "Depth" + CSV_SEPARATOR + "Field" + CSV_SEPARATOR + "Impact");

		for(int row = 0; row < arguments.size(); row++){
			Map<FieldName, ?> rowArguments = arguments.get(row);
			Map<FieldName, ?> rowResults = evaluator.evaluate(rowArguments);

			// The target value is a subclass of org.jpmml.evaluator.Computable,
			// and may expose different aspects of the prediction process by implementing org.jpmml.evaluator.ResultFeature subinterfaces.
			// The collection of exposed aspects depends on the model type, and evaluator run-time configuration
			// (exposing more aspects has slight memory/performance penalty, but nothing too serious).
			Object targetValue = rowResults.get(targetField.getName());
			//System.out.println(targetValue);

			printRow(row, targetValue, targetClass);
		}
	}

	static
	private void printRow(int row, Object targetValue, String targetClass){
		List<Contribution> contributions = new ArrayList<>();

		// Test, if the prediction coming from an ensemble model (aka segmentation model)?
		// For example, a random forest model
		if(targetValue instanceof HasSegmentation){
			HasSegmentation hasSegmentation = (HasSegmentation)targetValue;

			Collection<? extends SegmentResult> segmentResults = hasSegmentation.getSegmentResults();
			for(SegmentResult segmentResult : segmentResults){
				Object segmentTargetValue = segmentResult.getTargetValue();
				Number segmentWeight = segmentResult.getWeight();

				contributions.addAll(printDecisionPath(segmentResult.getEntityId(), segmentWeight, segmentTargetValue, targetClass));
			}
		} else

		// Not an ensemble model.
		// For example, a standalone decision tree model.
		{
			contributions.addAll(printDecisionPath(null, 1.0d, targetValue, targetClass));
		}

		for(Contribution contribution : contributions){
			System.out.println(row + CSV_SEPARATOR + contribution.getSegmentId() + CSV_SEPARATOR + contribution.getWeight() + CSV_SEPARATOR + contribution.getDepth() + CSV_SEPARATOR + contribution.getField() + CSV_SEPARATOR + contribution.getImpact());
		}
	}

	static
	private List<Contribution> printDecisionPath(String segmentId, Number weight, Object targetValue, String targetClass){
		List<Contribution> result = new ArrayList<>();

		HasDecisionPath hasDecisionPath = (HasDecisionPath)targetValue;

		Number parentClassProbability = 0d;

		List<Node> nodes = hasDecisionPath.getDecisionPath();
		for(int i = 0; i < nodes.size(); i++){
			Node node = nodes.get(i);

			if(!node.hasScoreDistributions()){
				continue;
			}

			Predicate predicate = node.getPredicate();
			Number recordCount = node.getRecordCount();
			Number classRecordCount = getRecordCount(node.getScoreDistributions(), targetClass);

			Number classProbability = (classRecordCount.doubleValue() / recordCount.doubleValue());

			Number impact = (classProbability.doubleValue() - parentClassProbability.doubleValue());

			result.add(new Contribution(segmentId, weight, i, predicate, impact));

			parentClassProbability = classProbability;
		}

		return result;
	}

	static
	private Number getRecordCount(List<ScoreDistribution> scoreDistributions, String targetClass){

		for(ScoreDistribution scoreDistribution : scoreDistributions){

			if((String.valueOf(scoreDistribution.getValue())).equals(targetClass)){
				return scoreDistribution.getRecordCount();
			}
		}

		throw new IllegalArgumentException(targetClass);
	}

	static
	private Evaluator loadEvaluator(File pmmlFile) throws Exception {
		// By default, the underlying JPMML-Model library optimizes the representation of decision tree models for memory-efficiency.
		// Memory-efficient Node subclasses are read-only.
		// As we are interested in rewriting parts of the decision tree data structure (reconstructing record counts at intermediate tree levels),
		// We must switch from the default optimizing node type adapter to a custom non-optimizing node type adapter.
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

		// Set for the current thread
		NodeAdapter.NODE_TRANSFORMER_PROVIDER.set(nodeTransformer);

		// It is possible to transfor and optimize the PMML class model object
		// (that was loaded from the PMML file) by running a collection of Visitor API classes over it.
		// In the current case, we want to reconstruct all record counts,
		// therefore we are running a custom ScoreDistributionGenerator visitor class before the default set of evaluation-type visitor classes.
		VisitorBattery visitors = new VisitorBattery();
		visitors.add(ScoreDistributionGenerator.class);
		visitors.addAll(new DefaultVisitorBattery());

		Evaluator evaluator = new LoadingModelEvaluatorBuilder()
			.setLocatable(false)
			.setVisitors(visitors)
			//.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS)
			.load(pmmlFile)
			.build();

		// Unset for the current thread
		NodeAdapter.NODE_TRANSFORMER_PROVIDER.remove();

		// Perforing the self-check
		evaluator.verify();

		return evaluator;
	}

	static
	private List<Map<FieldName, ?>> loadArguments(File csvFile) throws Exception {
		List<Map<FieldName, ?>> result = new ArrayList<>();

		try(InputStream is = new FileInputStream(csvFile)){
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));

			String header = reader.readLine();
			String[] headerCells = header.split(CSV_SEPARATOR);

			for(int row = 0; true; row++){
				String content = reader.readLine();
				if(content == null){
					break;
				}

				String[] contentCells = content.split(CSV_SEPARATOR);
				if(headerCells.length != contentCells.length){
					throw new IllegalArgumentException("Expected " + headerCells.length + " cells, got " + contentCells.length + " cells");
				}

				Map<FieldName, Object> arguments = new LinkedHashMap<>();

				for(int column = 0; column < headerCells.length; column++){
					arguments.put(FieldName.create(headerCells[column]), contentCells[column]);
				}

				result.add(arguments);
			}

			reader.close();
		}

		return result;
	}

	private static final String CSV_SEPARATOR = ",";
}
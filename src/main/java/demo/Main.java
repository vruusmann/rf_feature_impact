package demo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;
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

		Evaluator evaluator = loadEvaluator(pmmlFile);

		Map<FieldName, ?> arguments = loadArguments(csvFile);

		Map<FieldName, ?> results = evaluator.evaluate(arguments);

		// Supervised learning models are expected to have exactly one target field (aka label)
		TargetField targetField = Iterables.getOnlyElement(evaluator.getTargetFields());

		// The target value is a subclass of org.jpmml.evaluator.Computable,
		// and may expose different aspects of the prediction process by implementing org.jpmml.evaluator.ResultFeature subinterfaces.
		// The collection of exposed aspects depends on the model type, and evaluator run-time configuration
		// (exposing more aspects has slight memory/performance penalty, but nothing too serious).
		Object targetValue = results.get(targetField.getName());
		System.out.println(targetValue);

		// Test, if the prediction coming from an ensemble model (aka segmentation model)?
		// For example, a random forest model
		if(targetValue instanceof HasSegmentation){
			HasSegmentation hasSegmentation = (HasSegmentation)targetValue;

			Collection<? extends SegmentResult> segmentResults = hasSegmentation.getSegmentResults();
			for(SegmentResult segmentResult : segmentResults){
				Object segmentTargetValue = segmentResult.getTargetValue();

				printDecisionPath(segmentTargetValue);
			}
		} else

		// Not an ensemble model.
		// For example, a standalone decision tree model.
		{
			printDecisionPath(targetValue);
		}
	}

	static
	private void printDecisionPath(Object targetValue){
		System.out.println(targetValue);

		HasDecisionPath hasDecisionPath = (HasDecisionPath)targetValue;

		String indent = "";

		DecimalFormat probabilityFormat = new DecimalFormat("0.000");

		List<Node> nodes = hasDecisionPath.getDecisionPath();
		for(Node node : nodes){
			System.out.println(indent + node);

			Predicate predicate = node.getPredicate();

			// The PMML specification defines five predicate (boolean expression) types,
			// which are represented as org.dmg.pmml.Predicate subclasses.
			// The SimplePredicate is the most common one.
			// Ideally, this block of instanceof checks should be expanded to cover all org.dmg.pmml.Predicate subclasses.
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
	private Map<FieldName, ?> loadArguments(File csvFile) throws Exception {

		try(InputStream is = new FileInputStream(csvFile)){
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));

			String header = reader.readLine();
			String content = reader.readLine();

			reader.close();

			String[] headerCells = header.split(CSV_SEPARATOR);
			String[] contentCells = content.split(CSV_SEPARATOR);

			Map<FieldName, Object> arguments = new LinkedHashMap<>();

			for(int i = 0; i < headerCells.length; i++){
				arguments.put(FieldName.create(headerCells[i]), contentCells[i]);
			}

			return arguments;
		}
	}

	private static final String CSV_SEPARATOR = ",";
}
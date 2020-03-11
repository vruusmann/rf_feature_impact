package demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
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

	@Parameter (
		names = "--help",
		description = "Show the list of configuration options and exit",
		help = true
	)
	private boolean help = false;

	@Parameter (
		names = {"--pmml-model"},
		description = "PMML model file",
		required = true
	)
	private File modelFile = null;

	@Parameter (
		names = {"--target-class"},
		description = "Target class label for probabilistic classification models"
	)
	private String targetClass = null;

	@Parameter (
		names = {"--csv-input"},
		description = "CSV input file",
		required = true
	)
	private File inputFile = null;

	@Parameter (
		names = "--csv-output",
		description = "CSV output file",
		required = true
	)
	private File outputFile = null;


	static
	public void main(String... args) throws Exception {
		Main main = new Main();

		JCommander commander = new JCommander(main);
		commander.setProgramName(Main.class.getName());

		try {
			commander.parse(args);
		} catch(ParameterException pe){
			StringBuilder sb = new StringBuilder();

			sb.append(pe.toString());
			sb.append("\n");

			commander.usage(sb);

			System.err.println(sb.toString());

			System.exit(-1);
		}

		if(main.help){
			StringBuilder sb = new StringBuilder();

			commander.usage(sb);

			System.out.println(sb.toString());

			System.exit(0);
		}

		main.run();
	}

	private void run() throws Exception {
		Evaluator evaluator = loadEvaluator(this.modelFile);

		// Supervised learning models are expected to have exactly one target field (aka label)
		TargetField targetField = Iterables.getOnlyElement(evaluator.getTargetFields());

		Table inputTable;

		try(InputStream is = new FileInputStream(this.inputFile)){
			inputTable = CsvUtil.readTable(is, Main.CSV_SEPARATOR);
		}

		Table outputTable = new Table();
		outputTable.setSeparator(Main.CSV_SEPARATOR);

		outputTable.add(Arrays.asList("Id", "Segment", "Weight", "Depth", "Field", "Impact"));

		// The first line of the CSV input file is field names
		List<FieldName> headerRow = inputTable.remove(0).stream()
			.map(string -> FieldName.create(string))
			.collect(Collectors.toList());

		for(int row = 0; row < inputTable.size(); row++){
			Map<FieldName, Object> arguments = new LinkedHashMap<>();

			List<String> contentRow = inputTable.get(row);

			for(int column = 0; column < contentRow.size(); column++){
				arguments.put(headerRow.get(column), contentRow.get(column));
			}

			Map<FieldName, ?> results = evaluator.evaluate(arguments);

			// The target value is a subclass of org.jpmml.evaluator.Computable,
			// and may expose different aspects of the prediction process by implementing org.jpmml.evaluator.ResultFeature subinterfaces.
			// The collection of exposed aspects depends on the model type, and evaluator run-time configuration
			// (exposing more aspects has slight memory/performance penalty, but nothing too serious).
			Object targetValue = results.get(targetField.getName());
			//System.out.println(targetValue);

			printRow(String.valueOf(row), targetValue, this.targetClass, outputTable);
		}

		try(OutputStream os = new FileOutputStream(this.outputFile)){
			CsvUtil.writeTable(outputTable, os);
		}
	}

	static
	private void printRow(String id, Object targetValue, String targetClass, Table outputTable){
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
			List<String> row = Arrays.asList(id, contribution.getSegmentId(), contribution.getWeight(), contribution.getDepth(), contribution.getField(), contribution.getImpact()).stream()
				.map(object -> String.valueOf(object))
				.collect(Collectors.toList());

			outputTable.add(row);
		}
	}

	static
	private List<Contribution> printDecisionPath(String segmentId, Number weight, Object targetValue, String targetClass){
		List<Contribution> result = new ArrayList<>();

		HasDecisionPath hasDecisionPath = (HasDecisionPath)targetValue;

		Number parentClassProbability = 0d;
		Number parentScore = 0d;

		List<Node> nodes = hasDecisionPath.getDecisionPath();
		for(int i = 0; i < nodes.size(); i++){
			Node node = nodes.get(i);

			Predicate predicate = node.getPredicate();

			// Probabilistic classification
			if(node.hasScoreDistributions()){
				Number recordCount = node.getRecordCount();
				Number classRecordCount = getRecordCount(node.getScoreDistributions(), targetClass);

				Number classProbability = (classRecordCount.doubleValue() / recordCount.doubleValue());

				Number impact = (classProbability.doubleValue() - parentClassProbability.doubleValue());

				result.add(new Contribution(segmentId, weight, i, predicate, impact));

				parentClassProbability = classProbability;
			} else

			// Regression
			{
				Number score = (Number)node.getScore();

				Number impact = (score.doubleValue() - parentScore.doubleValue());

				result.add(new Contribution(segmentId, weight, i, predicate, impact));

				parentScore = score;
			}
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

	private static final String CSV_SEPARATOR = ",";
}
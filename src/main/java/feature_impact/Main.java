package feature_impact;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.Iterables;
import feature_impact.visitors.ScoreDistributionGenerator;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.adapters.NodeAdapter;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.mining.Segmentation.MultipleModelMethod;
import org.dmg.pmml.tree.ComplexNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.NodeTransformer;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.Regression;
import org.jpmml.evaluator.TargetField;
import org.jpmml.evaluator.UnsupportedElementException;
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
		names = {"--aggregate"},
		description = "Aggregate contributions by prediction",
		arity = 1
	)
	private boolean aggregate = false;

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
		ModelEvaluator<?> evaluator = loadModelEvaluator(this.modelFile);

		Model model = evaluator.getModel();

		if(model instanceof MiningModel){
			MiningModel miningModel = (MiningModel)model;

			Segmentation segmentation = miningModel.getSegmentation();

			MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
			switch(multipleModelMethod){
				case SUM:
				case WEIGHTED_SUM:
				case AVERAGE:
				case WEIGHTED_AVERAGE:
				case MEDIAN:
				case WEIGHTED_MEDIAN:
					break;
				default:
					throw new UnsupportedElementException(miningModel);
			}
		} else

		if(model instanceof TreeModel){
			// Pass
		} else

		{
			throw new UnsupportedElementException(model);
		}

		// Supervised learning models are expected to have exactly one target field (aka label)
		TargetField targetField = Iterables.getOnlyElement(evaluator.getTargetFields());

		Table inputTable;

		try(InputStream is = new FileInputStream(this.inputFile)){
			inputTable = CsvUtil.readTable(is, Main.CSV_SEPARATOR);
		}

		// The first line of the CSV input file is field names
		List<FieldName> inputFields = inputTable.remove(0).stream()
			.map(string -> FieldName.create(string))
			.collect(Collectors.toList());

		Table outputTable = new Table();
		outputTable.setSeparator(Main.CSV_SEPARATOR);

		if(this.aggregate){
			outputTable.setHeader(Arrays.asList(Contribution.TRUE.getValue()));
		} else

		{
			outputTable.setHeader(Arrays.asList("Id", "Segment", "Weight", "Depth", "Field", "Impact"));
		}

		for(int row = 0; row < inputTable.size(); row++){
			List<String> content = inputTable.get(row);

			Map<FieldName, Object> arguments = new LinkedHashMap<>();

			for(int column = 0; column < content.size(); column++){
				arguments.put(inputFields.get(column), content.get(column));
			}

			Map<FieldName, ?> results = evaluator.evaluate(arguments);

			// The target value is a subclass of org.jpmml.evaluator.Computable,
			// and may expose different aspects of the prediction process by implementing org.jpmml.evaluator.ResultFeature subinterfaces.
			// The collection of exposed aspects depends on the model type, and evaluator run-time configuration
			// (exposing more aspects has slight memory/performance penalty, but nothing too serious).
			Object targetValue = results.get(targetField.getName());
			//System.out.println(targetValue);

			computeExplanation(String.valueOf(row), targetValue, this.targetClass, this.aggregate, outputTable);
		}

		List<String> headerRow = outputTable.getHeader();
		for(int i = 1; i < outputTable.size(); i++){
			List<String> bodyRow = outputTable.get(i);

			while(bodyRow.size() < headerRow.size()){
				bodyRow.add(String.valueOf(null));
			}
		}

		try(OutputStream os = new FileOutputStream(this.outputFile)){
			CsvUtil.writeTable(outputTable, os);
		}
	}

	static
	private void computeExplanation(String id, Object targetValue, String targetClass, boolean aggregate, Table outputTable){
		List<Contribution> contributions = new ArrayList<>();

		int size = 1;

		// Test, if the prediction coming from an ensemble model (aka segmentation model)?
		// For example, a random forest model
		if(targetValue instanceof HasSegmentation){
			HasSegmentation hasSegmentation = (HasSegmentation)targetValue;

			Collection<? extends SegmentResult> segmentResults = hasSegmentation.getSegmentResults();

			size = segmentResults.size();

			for(SegmentResult segmentResult : segmentResults){
				Object segmentTargetValue = segmentResult.getTargetValue();
				Number segmentWeight = segmentResult.getWeight();

				contributions.addAll(computeFeatureContributions(segmentResult.getEntityId(), segmentWeight, segmentTargetValue, targetClass));
			}
		} else

		// Not an ensemble model.
		// For example, a standalone decision tree model.
		{
			contributions.addAll(computeFeatureContributions(null, 1.0d, targetValue, targetClass));
		} // End if

		if(aggregate){
			Map<FieldName, Double> aggregateImpacts = contributions.stream()
				.collect(Collectors.groupingBy(Contribution::getField, Collectors.mapping(Contribution::getImpact, Collectors.summingDouble(Number::doubleValue))));

			List<String> header = outputTable.getHeader();

			Set<FieldName> fieldNames = header.stream()
				.map(FieldName::create)
				.collect(Collectors.toCollection( LinkedHashSet::new));

			boolean changed = fieldNames.addAll(aggregateImpacts.keySet());
			if(changed){
				header = fieldNames.stream()
					.map(FieldName::getValue)
					.collect(Collectors.toList());

				outputTable.setHeader(header);
			}

			List<String> row = new ArrayList<>();

			for(FieldName fieldName : fieldNames){
				Double aggregateImpact = aggregateImpacts.get(fieldName);

				if((fieldName).equals(Contribution.TRUE)){
					aggregateImpact = (aggregateImpact.doubleValue() / size);
				}

				row.add(String.valueOf(aggregateImpact));
			}

			outputTable.add(row);
		} else

		{
			for(Contribution contribution : contributions){
				List<String> row = Arrays.asList(id, contribution.getSegmentId(), contribution.getWeight(), contribution.getDepth(), contribution.getField(), contribution.getImpact()).stream()
					.map(object -> String.valueOf(object))
					.collect(Collectors.toList());

				outputTable.add(row);
			}
		}
	}

	static
	private List<Contribution> computeFeatureContributions(String segmentId, Number weight, Object targetValue, String targetClass){
		List<Contribution> result = new ArrayList<>();

		HasDecisionPath hasDecisionPath = (HasDecisionPath)targetValue;

		Number parentClassProbability = 0d;
		Number parentScore = 0d;

		List<Node> nodes = hasDecisionPath.getDecisionPath();
		for(int i = 0; i < nodes.size(); i++){
			Node node = nodes.get(i);

			Predicate predicate = node.getPredicate();

			// Probabilistic classification
			if(targetValue instanceof Classification){

				if(!node.hasScoreDistributions()){
					throw new UnsupportedElementException(node);
				}

				Number recordCount = node.getRecordCount();
				Number classRecordCount = getRecordCount(node.getScoreDistributions(), targetClass);

				Number classProbability = (classRecordCount.doubleValue() / recordCount.doubleValue());

				Number impact = (classProbability.doubleValue() - parentClassProbability.doubleValue());

				result.add(new Contribution(segmentId, weight, i, predicate, impact));

				parentClassProbability = classProbability;
			} else

			// Regression
			if(targetValue instanceof Regression){

				if(!node.hasScore()){
					throw new UnsupportedElementException(node);
				}

				Number score = (Number)node.getScore();

				Number impact = (score.doubleValue() - parentScore.doubleValue());

				result.add(new Contribution(segmentId, weight, i, predicate, impact));

				parentScore = score;
			} else

			{
				throw new IllegalArgumentException();
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
	private ModelEvaluator<?> loadModelEvaluator(File pmmlFile) throws Exception {
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

		ModelEvaluator<?> modelEvaluator = new LoadingModelEvaluatorBuilder()
			.setLocatable(false)
			.setVisitors(visitors)
			//.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS)
			.load(pmmlFile)
			.build();

		// Unset for the current thread
		NodeAdapter.NODE_TRANSFORMER_PROVIDER.remove();

		// Perforing the self-check
		modelEvaluator.verify();

		return modelEvaluator;
	}

	private static final String CSV_SEPARATOR = ",";
}
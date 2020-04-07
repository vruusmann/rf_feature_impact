package feature_impact;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import feature_impact.visitors.ScoreDistributionGenerator;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.adapters.NodeAdapter;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.mining.Segmentation.MultipleModelMethod;
import org.dmg.pmml.tree.ComplexNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.NodeTransformer;
import org.dmg.pmml.tree.PMMLAttributes;
import org.dmg.pmml.tree.PMMLElements;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.MissingAttributeException;
import org.jpmml.evaluator.MissingElementException;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.Regression;
import org.jpmml.evaluator.TargetField;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.mining.HasSegmentation;
import org.jpmml.evaluator.mining.SegmentResult;
import org.jpmml.evaluator.tree.HasDecisionPath;
import org.jpmml.evaluator.visitors.DefaultVisitorBattery;
import org.jpmml.model.VisitorBattery;
import org.jpmml.model.visitors.FieldReferenceFinder;

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

		// Supervised learning models are expected to have exactly one target field (aka label)
		TargetField targetField = Iterables.getOnlyElement(evaluator.getTargetFields());

		MiningModel targetClassModel = null;

		Model model = evaluator.getModel();

		if(model instanceof MiningModel){
			MiningModel miningModel = (MiningModel)model;

			Segmentation segmentation = miningModel.getSegmentation();

			MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
			switch(multipleModelMethod){
				case MODEL_CHAIN:
					{
						List<Segment> segments = segmentation.getSegments();

						List<?> targetClasses = targetField.getCategories();

						int targetClassIndex = targetClasses.indexOf(this.targetClass);
						if(targetClassIndex < 0){
							throw new IllegalArgumentException("Cannot find target class " + this.targetClass + " in " + targetClasses);
						}

						int modelChainIndex;

						// A binary classification model
						if(targetClasses.size() == 2 && segments.size() == 2){
							modelChainIndex = 0;
						} else

						// A multi-class classification model
						if(targetClasses.size() >= 3 && (segments.size() == targetClasses.size() + 1)){
							modelChainIndex = targetClassIndex;
						} else

						{
							throw new UnsupportedElementException(segmentation);
						}

						Segment segment = segments.get(modelChainIndex);

						targetClassModel = (MiningModel)segment.getModel();
					}
					break;
				case SUM:
				case WEIGHTED_SUM:
				case AVERAGE:
				case WEIGHTED_AVERAGE:
				case MEDIAN:
				case WEIGHTED_MEDIAN:
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}
		} else

		if(model instanceof TreeModel){
			// Pass
		} else

		{
			throw new UnsupportedElementException(model);
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.inputFile), "UTF-8"));

		Splitter splitter = Splitter.on(Main.CSV_SEPARATOR);

		// The first line of the CSV input file is field names
		String inputHeaderRow = reader.readLine();

		List<FieldName> inputFields = (splitter.splitToList(inputHeaderRow)).stream()
			.map(string -> FieldName.create(string))
			.collect(Collectors.toList());

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.outputFile), "UTF-8"));

		Joiner joiner = Joiner.on(Main.CSV_SEPARATOR);

		List<FieldName> aggregateColumns = null;

		if(this.aggregate){
			Set<FieldName> names = new LinkedHashSet<>();
			names.add(Contribution.TRUE);

			FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
			fieldReferenceFinder.applyTo(model);

			names.addAll(fieldReferenceFinder.getFieldNames());

			aggregateColumns = new ArrayList<>(names);

			List<String> columnNames = aggregateColumns.stream()
				.map(name -> name.getValue())
				.collect(Collectors.toList());

			writer.write(joiner.join(columnNames) + "\n");
		} else

		{
			List<String> columnNames = Arrays.asList("Id", "Segment", "Weight", "Depth", "Field", "Impact");

			writer.write(joiner.join(columnNames) + "\n");
		}

		for(int row = 0; true; row++){
			String contentRow = reader.readLine();
			if(contentRow == null){
				break;
			}

			List<String> content = splitter.splitToList(contentRow);

			Map<FieldName, Object> arguments = new LinkedHashMap<>();

			for(int column = 0; column < content.size(); column++){
				arguments.put(inputFields.get(column), content.get(column));
			}

			Map<FieldName, ?> results;

			try {
				results = evaluator.evaluate(arguments);
			} catch(Exception e){
				System.err.println("Failed to evaluate row " + (row) + " (" + arguments + ")");

				throw e;
			}

			// The target value is a subclass of org.jpmml.evaluator.Computable,
			// and may expose different aspects of the prediction process by implementing org.jpmml.evaluator.ResultFeature subinterfaces.
			// The collection of exposed aspects depends on the model type, and evaluator run-time configuration
			// (exposing more aspects has slight memory/performance penalty, but nothing too serious).
			Object targetValue = results.get(targetField.getName());
			//System.out.println(targetValue);

			List<List<String>> resultRows = computeExplanation(String.valueOf(row), targetValue, targetClassModel, this.targetClass, this.aggregate, aggregateColumns);
			for(List<String> resultRow : resultRows){
				writer.write(joiner.join(resultRow) + "\n");
			}
		}

		reader.close();

		writer.close();
	}

	static
	private List<List<String>> computeExplanation(String id, Object targetValue, MiningModel targetClassModel, String targetClass, boolean aggregate, List<FieldName> fieldNames){
		List<Contribution> contributions = new ArrayList<>();

		if(targetClassModel != null){
			Object targetClassValue = ExtendedMiningModelEvaluator.RESULTS.get(targetClassModel);

			if(targetClassModel == null){
				throw new RuntimeException();
			}

			targetValue = targetClassValue;
		}

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

		List<List<String>> result = new ArrayList<>();

		if(aggregate){
			Map<FieldName, Double> aggregateImpacts = contributions.stream()
				.collect(Collectors.groupingBy(Contribution::getField, Collectors.mapping(Contribution::getImpact, Collectors.summingDouble(Number::doubleValue))));

			List<String> row = new ArrayList<>();

			for(FieldName fieldName : fieldNames){
				Double aggregateImpact = aggregateImpacts.get(fieldName);

				if((fieldName).equals(Contribution.TRUE)){
					aggregateImpact = (aggregateImpact.doubleValue() / size);
				}

				row.add(String.valueOf(aggregateImpact));
			}

			result.add(row);
		} else

		{
			for(Contribution contribution : contributions){
				List<String> row = Arrays.asList(id, contribution.getSegmentId(), contribution.getWeight(), contribution.getDepth(), contribution.getField(), contribution.getImpact()).stream()
					.map(object -> String.valueOf(object))
					.collect(Collectors.toList());

				result.add(row);
			}
		}

		return result;
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
					throw new MissingElementException(node, PMMLElements.COMPLEXNODE_SCOREDISTRIBUTIONS);
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
					throw new MissingAttributeException(node, PMMLAttributes.COMPLEXNODE_SCORE);
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
			.setModelEvaluatorFactory(new ExtendedModelEvaluatorFactory())
			.build();

		// Unset for the current thread
		NodeAdapter.NODE_TRANSFORMER_PROVIDER.remove();

		// Perforing the self-check
		modelEvaluator.verify();

		return modelEvaluator;
	}

	private static final String CSV_SEPARATOR = ",";
}
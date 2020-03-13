package feature_impact;

import java.util.IdentityHashMap;
import java.util.Map;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.evaluator.ModelEvaluationContext;
import org.jpmml.evaluator.TargetField;
import org.jpmml.evaluator.mining.MiningModelEvaluator;

public class ExtendedMiningModelEvaluator extends MiningModelEvaluator {

	public ExtendedMiningModelEvaluator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);
	}

	@Override
	public Map<FieldName, ?> evaluateInternal(ModelEvaluationContext context){
		MiningModel miningModel = getModel();

		TargetField targetField = getTargetField();

		Map<FieldName, ?> result = super.evaluateInternal(context);

		Object targetValue = result.get(targetField.getName());

		ExtendedMiningModelEvaluator.RESULTS.put(miningModel, targetValue);

		return result;
	}

	public static final Map<MiningModel, Object> RESULTS = new IdentityHashMap<>();
}
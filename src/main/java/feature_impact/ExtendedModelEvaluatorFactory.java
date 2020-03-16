package feature_impact;

import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.ModelEvaluatorFactory;

public class ExtendedModelEvaluatorFactory extends ModelEvaluatorFactory {

	@Override
	public ModelEvaluator<?> newModelEvaluator(PMML pmml, Model model){

		if(model instanceof MiningModel){
			MiningModel miningModel = (MiningModel)model;

			return new ExtendedMiningModelEvaluator(pmml, miningModel);
		}

		return super.newModelEvaluator(pmml, model);
	}
}
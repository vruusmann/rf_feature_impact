package feature_impact;

import java.lang.reflect.Field;

import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.evaluator.Configuration;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.ModelEvaluatorFactory;

public class ExtendedModelEvaluatorFactory extends ModelEvaluatorFactory {

	@Override
	public ModelEvaluator<?> newModelEvaluator(PMML pmml, Model model){
		Configuration configuration = getConfiguration();

		if(model instanceof MiningModel){
			MiningModel miningModel = (MiningModel)model;

			ModelEvaluator<?> modelEvaluator = new ExtendedMiningModelEvaluator(pmml, miningModel);
			modelEvaluator.configure(configuration);

			return modelEvaluator;
		}

		return super.newModelEvaluator(pmml, model);
	}

	private Configuration getConfiguration(){

		try {
			Field field = ModelEvaluatorFactory.class.getDeclaredField("configuration");

			if(!field.isAccessible()){
				field.setAccessible(true);
			}

			return (Configuration)field.get(this);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}
	}
}
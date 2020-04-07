from lightgbm import LGBMClassifier, LGBMRegressor
from sklearn_pandas import DataFrameMapper
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.preprocessing import OneHotEncoder
from sklearn.tree import DecisionTreeClassifier, DecisionTreeRegressor
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.decoration import CategoricalDomain, ContinuousDomain
from sklearn2pmml.pipeline import PMMLPipeline
from sklearn2pmml.preprocessing.lightgbm import make_lightgbm_dataframe_mapper
from sklearn2pmml.preprocessing.xgboost import make_xgboost_dataframe_mapper
from xgboost.sklearn import XGBClassifier, XGBRegressor

import pandas

audit = pandas.read_csv("csv/Audit.csv")
print(audit.head(3))

columns = audit.columns.tolist()

audit_X = audit[columns[: -1]]
audit_y = audit[columns[-1]]

audit_X = audit_X.drop(["Deductions"], axis = 1)

def lightgbm_audit():
	mapper, categorical_feature = make_lightgbm_dataframe_mapper(audit_X.dtypes, missing_value_aware = False)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("classifier", LGBMClassifier(n_estimators = 71, max_depth = 7, random_state = 13))
	])
	pipeline.fit(audit_X, audit_y, classifier__categorical_feature = categorical_feature)
	pipeline.configure(compact = True)
	sklearn2pmml(pipeline, "pmml/LightGBMAudit.pmml", with_repr = False)

lightgbm_audit()

def sklearn_audit(classifier, name):
	pipeline = PMMLPipeline([
		("mapper", DataFrameMapper(
			[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["Employment", "Education", "Marital", "Occupation", "Gender"]] +
			[([column], ContinuousDomain()) for column in ["Age", "Income", "Hours"]]
		)),
		("classifier", classifier)
	])
	pipeline.fit(audit_X, audit_y)
	pipeline.configure(compact = False)
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml", with_repr = False)

sklearn_audit(DecisionTreeClassifier(max_depth = 8, random_state = 13), "DecisionTreeAudit")
sklearn_audit(RandomForestClassifier(n_estimators = 71, max_depth = 7, random_state = 13), "RandomForestAudit")

def xgboost_audit():
	mapper = make_xgboost_dataframe_mapper(audit_X.dtypes, missing_value_aware = False)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("classifier", XGBClassifier(n_estimators = 71, max_depth = 5, random_state = 13))
	])
	pipeline.fit(audit_X, audit_y)
	pipeline.configure(compact = True)
	sklearn2pmml(pipeline, "pmml/XGBoostAudit.pmml", with_repr = True)

xgboost_audit()

auto = pandas.read_csv("csv/Auto.csv")
print(auto.head(3))

columns = auto.columns.tolist()

auto_X = auto[columns[: -1]]
auto_y = auto[columns[-1]]

def lightgbm_auto():
	mapper, categorical_feature = make_lightgbm_dataframe_mapper(auto_X.dtypes, missing_value_aware = False)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("regressor", LGBMRegressor(n_estimators = 31, max_depth = 5, random_state = 13))
	])
	pipeline.fit(auto_X, auto_y, regressor__categorical_feature = categorical_feature)
	pipeline.configure(compact = True)
	sklearn2pmml(pipeline, "pmml/LightGBMAuto.pmml", with_repr = False)

lightgbm_auto()

def sklearn_auto(regressor, name):
	pipeline = PMMLPipeline([
		("mapper", DataFrameMapper(
			[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["cylinders", "model_year", "origin"]] +
			[([column], ContinuousDomain()) for column in ["displacement", "horsepower", "weight", "acceleration"]]
		)),
		("regressor", regressor)
	])
	pipeline.fit(auto_X, auto_y)
	pipeline.configure(compact = False)
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml", with_repr = False)

sklearn_auto(DecisionTreeRegressor(max_depth = 6, random_state = 13), "DecisionTreeAuto")
sklearn_auto(RandomForestRegressor(n_estimators = 31, max_depth = 5, random_state = 13), "RandomForestAuto")

def xgboost_auto():
	mapper = make_xgboost_dataframe_mapper(auto_X.dtypes, missing_value_aware = False)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("regressor", XGBRegressor(n_estimators = 31, max_depth = 3, random_state = 13))
	])
	pipeline.fit(auto_X, auto_y)
	pipeline.configure(compact = False)
	sklearn2pmml(pipeline, "pmml/XGBoostAuto.pmml", with_repr = True)

xgboost_auto()
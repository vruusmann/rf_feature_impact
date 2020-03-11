from sklearn_pandas import DataFrameMapper
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.preprocessing import OneHotEncoder
from sklearn.tree import DecisionTreeClassifier, DecisionTreeRegressor
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.decoration import CategoricalDomain, ContinuousDomain
from sklearn2pmml.pipeline import PMMLPipeline

import pandas

audit = pandas.read_csv("csv/Audit.csv")
print(audit.head(3))

def sklearn_audit(classifier, name):
	pipeline = PMMLPipeline([
		("mapper", DataFrameMapper(
			[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["Employment", "Education", "Marital", "Occupation", "Gender"]] +
			[([column], ContinuousDomain()) for column in ["Age", "Income", "Hours"]]
		)),
		("classifier", classifier)
	])
	pipeline.fit(audit, audit["Adjusted"])
	pipeline.configure(compact = False)
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml", with_repr = False)

sklearn_audit(DecisionTreeClassifier(max_depth = 8, random_state = 13), "DecisionTreeAudit")
sklearn_audit(RandomForestClassifier(n_estimators = 7, max_depth = 5, random_state = 13), "RandomForestAudit")

auto = pandas.read_csv("csv/Auto.csv")
print(auto.head(3))

def sklearn_auto(regressor, name):
	pipeline = PMMLPipeline([
		("mapper", DataFrameMapper(
			[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["cylinders", "model_year", "origin"]] +
			[([column], ContinuousDomain()) for column in ["displacement", "horsepower", "weight", "acceleration"]]
		)),
		("regressor", regressor)
	])
	pipeline.fit(auto, auto["mpg"])
	pipeline.configure(compact = False)
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml", with_repr = False)

sklearn_auto(DecisionTreeRegressor(max_depth = 6, random_state = 13), "DecisionTreeAuto")
sklearn_auto(RandomForestRegressor(n_estimators = 5, max_depth = 3, random_state = 13), "RandomForestAuto")
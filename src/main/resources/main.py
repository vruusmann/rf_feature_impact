from sklearn_pandas import DataFrameMapper
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.preprocessing import OneHotEncoder
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.decoration import CategoricalDomain, ContinuousDomain
from sklearn2pmml.pipeline import PMMLPipeline

import pandas

audit = pandas.read_csv("csv/Audit.csv")
print(audit.head(3))

pipeline = PMMLPipeline([
	("mapper", DataFrameMapper(
		[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["Employment", "Education", "Marital", "Occupation", "Gender"]] +
		[([column], ContinuousDomain()) for column in ["Age", "Income", "Hours"]]
	)),
	("classifier", RandomForestClassifier(n_estimators = 7, max_depth = 5, random_state = 13))
])
pipeline.fit(audit, audit["Adjusted"])
pipeline.configure(compact = False)

sklearn2pmml(pipeline, "pmml/RandomForestClassifier.pmml", with_repr = False)

auto = pandas.read_csv("csv/Auto.csv")
print(auto.head(3))

pipeline = PMMLPipeline([
	("mapper", DataFrameMapper(
		[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["cylinders", "model_year", "origin"]] +
		[([column], ContinuousDomain()) for column in ["displacement", "horsepower", "weight", "acceleration"]]
	)),
	("regressor", RandomForestRegressor(n_estimators = 5, max_depth = 3, random_state = 13))
])
pipeline.fit(auto, auto["mpg"])
pipeline.configure(compact = False)

sklearn2pmml(pipeline, "pmml/RandomForestRegressor.pmml", with_repr = False)

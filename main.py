from sklearn.datasets import load_wine
from sklearn2pmml.pipeline import PMMLPipeline
from sklearn2pmml import sklearn2pmml
from sklearn.ensemble import RandomForestClassifier
import pandas as pd

a = load_wine()

X = pd.DataFrame(data=a['data'], columns=a['feature_names'])
y = a['target']
SEED = 30

X.to_csv("wine.csv", index = False)

pipeline = PMMLPipeline([
  ('classifier', RandomForestClassifier(n_estimators=3, max_depth=4, random_state=SEED))
])

pipeline.fit(X, y)

pipeline.configure(compact = False)
sklearn2pmml(pipeline, "Classifier.pmml", with_repr = True)

pipeline.configure(compact = True)
sklearn2pmml(pipeline, "CompactClassifier.pmml", with_repr = True)
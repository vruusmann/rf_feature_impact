# Installation #

Build using Apache Maven:

```
$ mvn clean package
```

The build produces an executable uber-JAR file `target/demo-executable-1.0-SNAPSHOT.jar`.

# Usage #

Run Python script:

```
$ python main.py
```

The training session produces two PMML files `Classifier.pmml` and `CompactClassifier.pmml`, and a CSV file `demo_X.csv`.

Score PMML files with the CSV file:

```
$ java -jar target/demo-executable-1.0-SNAPSHOT.jar Classifier.pmml demo_X.csv
$ java -jar target/demo-executable-1.0-SNAPSHOT.jar CompactClassifier.pmml demo_X.csv
```
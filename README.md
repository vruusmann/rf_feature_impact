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

The training session produces a PMML file `SampleClassifier.pmml` and a CSV file `demo_X.csv`.

Score the PMML file with the CSV file:

```
$ java -jar target/demo-executable-1.0-SNAPSHOT.jar SampleClassifier.pmml demo_X.csv
```
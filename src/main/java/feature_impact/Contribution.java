package feature_impact;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.Predicate;

public class Contribution {

	private String segmentId = null;

	private Number weight = null;

	private int depth = 0;

	private Predicate predicate = null;

	private Number impact = null;


	public Contribution(String segmentId, Number weight, int depth, Predicate predicate, Number impact){
		setSegmentId(segmentId);
		setWeight(weight);
		setDepth(depth);
		setPredicate(predicate);
		setImpact(impact);
	}

	public String getSegmentId(){
		return this.segmentId;
	}

	private void setSegmentId(String segmentId){
		this.segmentId = segmentId;
	}

	public Number getWeight(){
		return this.weight;
	}

	private void setWeight(Number weight){
		this.weight = weight;
	}

	public int getDepth(){
		return this.depth;
	}

	private void setDepth(int depth){
		this.depth = depth;
	}

	public FieldName getField(){
		Predicate predicate = getPredicate();

		if(predicate instanceof HasFieldReference){
			HasFieldReference<?> hasFieldReference = (HasFieldReference<?>)predicate;

			return hasFieldReference.getField();
		}

		return Contribution.TRUE;
	}

	public Predicate getPredicate(){
		return this.predicate;
	}

	private void setPredicate(Predicate predicate){
		this.predicate = predicate;
	}

	public Number getImpact(){
		return this.impact;
	}

	private void setImpact(Number impact){
		this.impact = impact;
	}

	public static final FieldName TRUE = FieldName.create("(true)");
}
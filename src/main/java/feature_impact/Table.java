package feature_impact;

import java.util.ArrayList;
import java.util.List;

public class Table extends ArrayList<List<String>> {

	private String separator = null;


	public Table(){
		super(1024);
	}

	public List<String> getHeader(){
		return get(0);
	}

	public void setHeader(List<String> header){

		if(isEmpty()){
			add(header);
		} else

		{
			set(0, header);
		}
	}

	public String getSeparator(){
		return this.separator;
	}

	public void setSeparator(String separator){
		this.separator = separator;
	}
}
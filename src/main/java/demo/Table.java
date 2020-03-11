package demo;

import java.util.ArrayList;
import java.util.List;

public class Table extends ArrayList<List<String>> {

	private String separator = null;


	public Table(){
		super(1024);
	}

	public String getSeparator(){
		return this.separator;
	}

	public void setSeparator(String separator){
		this.separator = separator;
	}
}
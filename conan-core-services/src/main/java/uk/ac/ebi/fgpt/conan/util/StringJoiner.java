package uk.ac.ebi.fgpt.conan.util;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class StringJoiner {

	private List<String> parts;
	private String sep;
	
	public StringJoiner(String separator) {
		this.parts = new ArrayList<String>();
		this.sep = separator;
	}
	
	public void add(String part) {		
		this.add("", part);
	}
	
	public void add(String prefix, String part) {
		this.add(true, prefix, part);
	}
	
	public void add(boolean test, String prefix, String part) {
		if (test && part != null && !part.isEmpty()) {
			this.parts.add((prefix == null ? "" : prefix) + part);
		}
	}
	
	public void add(Object obj) {
		this.add("", obj);
	}
	
	public void add(String prefix, Object obj) {
		if (obj != null) {
			String str = obj.toString();
			if (str != null && !str.isEmpty()) {
				this.parts.add((prefix == null ? "" : prefix) + str);
			}
		}
	}
	
	public String toString() {		
		return StringUtils.join(this.parts, this.sep == null ? " " : this.sep);
	}
}

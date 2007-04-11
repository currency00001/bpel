/*******************************************************************************
 * Copyright (c) 2006 Oracle Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Oracle Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.bpel.validator.model;

/**
 * Java JDK dependencies here please ...
 */

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.namespace.QName;


/**
 * @author Michal Chmielewski (michal.chmielewski@oracle.com)
 * @date Sep 14, 2006
 *
 */

@SuppressWarnings("nls")

public class Rules  {	
	
	/**
	 * Rules are methods whose name follows the pattern
	 * <pre>
	 *    rule_<RuleName>_<Index>
	 * </pre>
	 * where <Index> is a number. If index is missing as a suffix, 
	 * then it is assumed it will be 0. 
	 * <p>
	 * Rules will be ordered according to the index and executed 
	 * in that order.
	 * 
	 */
	
	
 	final static String RULE_NAME_PREFIX = "rule_";  //$NON-NLS-1$
 	 	
 	/**
 	 * The rules discovered from introspection ...
 	 */
	
	List<Rule> mRules = new ArrayList<Rule>();
	
	
	/**
	 * The registry of such rules ...
	 */
		
	static final Map<Class<? extends Validator>,Rules> RULES_BY_CLASS = new HashMap<Class<? extends Validator>,Rules> ();	
	
	
	/**
	 * Return the rules for the given class object. Rules are just methods
	 * with special names. The class's inherited members are also introspected to
	 * see if rule methods are inherited. The rule methods must be public
	 * and take no arguments.
	 * 
	 * @param clazz
	 * @return the Rules object which contains the list of rules that will be run
	 */
	
	static final public Rules getRules ( Class<? extends Validator> clazz ) {
		
		Rules rules = RULES_BY_CLASS.get( clazz );
		if (rules != null) {
			return rules;
		}
		
		// compute them
		rules = new Rules ( clazz );
		
		// store for later use
		synchronized ( RULES_BY_CLASS ) {
			RULES_BY_CLASS.put(clazz, rules);
		}
		return rules;
	}
	
	
	
	/** 
	 * Create a new set of rules for the given class by introspecting it.
	 * 
	 * @param clazz
	 */
	
	
	public Rules ( Class<? extends Validator> clazz ) {			
						
		for(Method m : clazz.getMethods()) {							
			if (isRule(m)) {
				mRules.add( new Rule(m));
			}					
		}				
		if (mRules.size() > 1) {
			// sort according to suffix digit
			Collections.sort( mRules );
		}		
	}
	
	
	/**
	 * Run the rules in the order intended. This is simply an iteration over
	 * the rules discovered for the given class. 
	 * <p>
	 * Each of the validator classes can dynamically disabled a rule from being
	 * run. 
	 * <p>
	 * 
	 * @param context the validator on which the rule will be called.
	 * @param tag the tag used on the rules.
	 * @param args to pass to the rules (if any).
	 */
		
	public void runRules ( Validator context, String tag, Object ... args ) {	
		
		for (Rule rule: mRules) {
			// wrong tag ?
			if (tag.equals(rule.getTag()) == false) {
				continue ;
			}

			if (context.startRule (rule) == false) {
				continue;
			}
			
	        // Now run the rule
			try {
				rule.invoke (context, args);
			} catch (Throwable t) {
				context.internalProblem ( rule ,t );	
				log (context, rule, t);
			}
			
			context.endRule ( rule );
		}
		
		
	}

	
	
	/**
	 * Log any errors during rule execution.
	 * 
	 * @param context
	 * @param rule
	 * @param t
	 */
	
	void log ( Object context, Rule rule , Throwable t) {
		
		p("Problem executing rule {0}, stack trace shown below",rule.getFullName()); //$NON-NLS-1$
		t.printStackTrace( System.out );
		return ;		
	}
	
	
	
	/**
	 * Answer true if the method passed is something that we understand to
	 * be a rule.
	 * @param m
	 * @return true if rule, false if not.
	 */
	
	static public boolean isRule ( Method m ) {
		return (m.getName().startsWith(RULE_NAME_PREFIX) ||	m.getAnnotation(ARule.class) != null);
	}	
	
	
	/**
	 * An IndexFilter. Filters out rules based on the index.
	 * 
	 * @author Michal Chmielewski (michal.chmielewski@oracle.com)
	 * @date Oct 12, 2006
	 */
	
	static public class IndexFilter implements IFilter<Rule> {
		int low;
		int high;
		String tag;
		
		/**
		 * Brand new index based filter.
		 * 
		 * @param l
		 * @param h
		 */
		public IndexFilter (int l, int h) {
			low  = Math.max(0, l);
			high = Math.min(h, 65536);
		}
		
		/**
		 * Index based filter. 
		 * @param l low value
		 * @param h high value
		 * @param t tag value
		 */
		
		public IndexFilter (int l, int h, String t) {
			this(l,h);
			tag = t;
		}


		/** (non-Javadoc)
		 * @param rule 
		 * @return true if to select, false otherwise.
		 */
		
		public boolean select (Rule rule) {
			boolean s = low <= rule.getIndex() && rule.getIndex() <= high;
			if (tag == null) {
				return s;
			}
			return s && tag.equals(rule.getTag()); 				
		}
	}
		
		
	
	/**
	 * A class that represents a simple validation rule.
	 * 
	 * Rules are automatically executed by the validator code. Zero argument rules are 
	 * called in the sequence implied by the rule methods. 
	 * 
	 * Rules which have some arguments must be called explicitely.
	 * 
	 * @author Michal Chmielewski (michal.chmielewski@oracle.com)
	 * @date Sep 20, 2006
	 */
	

	@SuppressWarnings("nls")
	
	public class Rule implements Comparable<Rule> {
		
		Method method;
		String name ;		
		int index;
		String fullName;
		ARule aRule;
	
		
		Rule ( Method m ) {
			
			method = m;
			aRule = method.getAnnotation(ARule.class);
			
			name = parseName( m.getName() );
			index = parseIndex ( m.getName() );
			fullName = m.getDeclaringClass().getSimpleName() + "." + name + "." + index;			
		}
				
		String parseName ( String n ) {
			
			int idx = n.lastIndexOf('_');
			int start = 0;
			if (n.startsWith(RULE_NAME_PREFIX)) {
				start = RULE_NAME_PREFIX.length();
			}
			if (idx > 0 && start < idx) {
				return n.substring(start , idx);
			}
			return n.substring(start);
		}
		
		int parseIndex ( String n ) {
			
			int idx = n.lastIndexOf('_');			
			if (idx < 0) {
				if (aRule != null) {
					return aRule.order();
				}
				return 0;
			}
			
			try {
				return Integer.parseInt( n.substring(idx+1) );
			} catch (NumberFormatException nfe) {
				if (aRule != null) {
					return aRule.order();
				}
				return 0;
			}
		}		
		
		/**
		 * Return the name of the rule.
		 * 
		 * @return the name of the rule
		 */
		public String getName() {
			return name;			
		}
		
		/**
		 * Return the index of the rule (the order in which it will be run).
		 * 
		 * @return the index of the rule
		 */
		public int getIndex() {
			return index;
		}
		
		/**
		 * 
		 * @return return the tag associated with this rule.
		 */
		
		public String getTag () {
			if (aRule == null) {
				return Validator.PASS1;
			}
			return aRule.tag();
		}
		
	
		
		/**
		 * Return the rule annotations for this rule.
		 * @return the rule annotations for this rule.
		 */
		
		public ARule getARule () {
			return aRule;
		}
		
		/**
		 * @param context
		 * @param args
		 * @return whatever the rule returns
		 * @throws Exception
		 */
		
		public Object invoke (Object context, Object args[]) throws Exception	{
			return method.invoke(context, args);
		}

		/**
		 * @param rule 
		 * @return the compare to result
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		
		public int compareTo (Rule rule) {
			return this.index - rule.index;
		}

		/**
		 * Return the full name of the Rule
		 * @return full name of the rule
		 */
		public String getFullName() {
			return fullName;
		}
	
	}
	
	
	
	static Comparator<Rule> SORTER = new Comparator<Rule> () {

		/** (non-Javadoc)
		 * @param o1 rule 1
		 * @param o2 rule 2
		 * @return result of compare
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Rule o1, Rule o2) {
			int res = o1.getTag().compareTo(o2.getTag());
			if (res != 0) {
				return res;
			}
			return o1.index - o2.index;
		}
	};
	
	
	static PrintStream OUT = System.out;

	/**
	 * Attempt to print documentation from the source about the types of rules
	 * present in the validator.
	 * 
	 * @param args
	 * @throws FileNotFoundException 
	 */

	@SuppressWarnings({ "nls", "boxing" })
	public static void main (String[] args) throws FileNotFoundException {
		
		
		// Hmm this should be somehow done in a different way ...
		RuleFactory.registerFactory( new org.eclipse.bpel.validator.rules.Factory () );
		RuleFactory.registerFactory( new org.eclipse.bpel.validator.xpath.Factory () );
		RuleFactory.registerFactory( new org.eclipse.bpel.validator.wsdl.Factory () );
		RuleFactory.registerFactory( new org.eclipse.bpel.validator.plt.Factory () );
		RuleFactory.registerFactory( new org.eclipse.bpel.validator.vprop.Factory () );
		
		
		// 
		Field fields[] = IConstants.class.getFields();
		
		if (args.length > 0) {
			OUT = new PrintStream(args[0]);
		}
		
		int totalRules = 0;
		
		Map<ARule,Rule> annotationHash = new TreeMap<ARule,Rule>(
				new Comparator<ARule>() {
					public int compare(ARule o1, ARule o2) {
						return o1.sa() - o2.sa();
					}					
				});
		
		List<QName> nodes = new LinkedList<QName>();
		
		// Fish out the nodes.
		for (Field f : fields) {
			
			String n = f.getName();
			if (n.startsWith("ND_") == false) { //$NON-NLS-1$
				continue;
			}			
			
			String bpelName ;
			try {
				Object o = f.get(null);
				if ((o instanceof String) == false) {
					continue;
				}
				bpelName =  (String) o;
			} catch (Exception ex) {
				continue;
			}
			
			QName qtype =  new QName( IConstants.XMLNS_BPEL, bpelName );
			nodes.add(qtype);
		}
		
		// For evaluating conditions
		
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_CONDITION) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_BRANCHES) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_FINAL_COUNTER_VALUE) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_START_COUNTER_VALUE) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_FOR) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_FROM) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_JOIN_CONDITION) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_REPEAT_EVERY) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_TO) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_TRANSITION_CONDITION) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_UNTIL) );
		nodes.add ( new QName ( IConstants.XMLNS_XPATH_EXPRESSION_LANGUAGE, IConstants.ND_QUERY) );
		
		
		
		
		// TODO: this needs to somehow be automated ...
		nodes.add ( new QName ( IConstants.XMLNS_PLNK,  IConstants.WSDL_ND_PARTNER_LINK_TYPE));
		nodes.add ( new QName ( IConstants.XMLNS_PLNK,  IConstants.WSDL_ND_ROLE));
		
		// Property aliases
		nodes.add ( new QName ( IConstants.XMLNS_VPROP, IConstants.WSDL_ND_PROPERTY));
		nodes.add ( new QName ( IConstants.XMLNS_VPROP, IConstants.WSDL_ND_PROPERTY_ALIAS));
		nodes.add ( new QName ( IConstants.XMLNS_VPROP, IConstants.WSDL_ND_QUERY));
		
		// Definitions node
		nodes.add ( new QName ( IConstants.XMLNS_WSDL,  IConstants.WSDL_ND_DEFINITIONS));
		
		// 
		// Sort on QName

		Collections.sort (nodes,
				new Comparator<QName>() {
					public int compare(QName o1, QName o2) {
						return o1.getLocalPart().compareTo( o2.getLocalPart() );
					}		
				}			
		);
		
		
		
		p("<h2>Validators</h2>");
		
		p("<ol class='validators'>");
		
		for(QName qtype: nodes) {
			
			Validator obj = RuleFactory.createValidator( qtype );
			
			if (obj == null) {
				p("<li><div class='missing'>{0} - no validator.</div></li>",  qtype); //$NON-NLS-1$
				continue;
			}
			
			Class<? extends Validator> clazz = obj.getClass();
						
			Rules rules = getRules(clazz);
			int length = rules.mRules.size();
			
			
			p("<li> <b>{0}</b><p>{1} rules in class <a href=\"source.php?c={2}\">{2}</a></p>",   //$NON-NLS-1$
					qtype,length,clazz.getName() ,clazz.getName() );
									
			totalRules += length;
			
			p("<table class='av2'>");
			
			p("<tr>");
			p("<th class='w1'>#</th>");
			p("<th class='w4'>Rule</th>");
			p("<th class='w1'>Seq</th>");
			p("<th class='w2'>Tag</th>");
			p("<th>Description</th>");			
			p("<th class='w2'>Date</th>");
			p("<th class='w1'>SA</th>");
			p("</tr>");
			
			int cnt = 0;
			List<Rule> rlist = new ArrayList<Rule>( rules.mRules );
			Collections.sort(rlist,SORTER);
			
			for(Rule r : rlist) {
				cnt += 1;
				
				p("<tr>");
				p(" <td>{0}</td>",cnt);
				p(" <td><a href=\"source.php?c={1}&m={2}\">{0}</a></td>",
						        r.name,clazz.getName(),r.method.getName());
				
				p(" <td>{0}</td>",r.getIndex());
				
				ARule a = r.method.getAnnotation( org.eclipse.bpel.validator.model.ARule.class );
				
				
				if (a != null) {
					p(" <td>{0}</td>",a.tag());
					p(" <td>{0}<br/><span class='author'>Author: {1}</span></td>", a.desc(), a.author() );					
					p(" <td>{0}</td>", a.date());
					p(" <td>{0}</td>", a.sa());					
					annotationHash.put(a,r);
				} else {
					p(" <td>{0}</td>",Validator.PASS1);
					p(" <td colspan='2'>-</td>");
					p(" <td>0</td>");
				}
				p("</tr>");
			}
			
			p("</table>");
		}
		p("</ol>");
		
		
		
		// Stats
		
		p("<h2>Statistics </h2>");
		p("<table class='av'>");
		p(" <tr><th>Total Rules:</th><td>{0}</td></tr>",totalRules);
		p(" <tr><th>Annotated Rules:</th><td>{0}</td></tr>",annotationHash.size() );		
		p(" <tr><th>Total Nodes:</th><td>{0}</td></tr>",nodes.size());		
		p("</table>");
		
		// Print the list of static analysis checks which are made
		// which reference the SA codes from the BPEL spec. These point to the
		// rules where these checks are done.
		
		p("<h2>SA Checks done (against the spec)</h2>");		
		
		p("<table class='av2'>");
		p("<tr>");
		p("<th class='w1'>SA</th>");
		p("<th>Description</th>");		
		p("<th class='w5'>Method</th>");
		p("</tr>");
		
		
		int saNumber = 0;
		
		int missingSA = 0;
		int totalSA = 94;
		
		for(ARule a : annotationHash.keySet()) {
			
			if (a.sa() <= 0) {
				continue;
			}
			
			if (a.sa() - saNumber > 1 && a.sa() <= totalSA ) {
				for( int i=saNumber + 1, j = a.sa(); i < j; i++) {
					missingSA += 1;
					p("<tr>");
					p(" <td class='warn'>{0}</td>", i);
					p(" <td colspan='2'><div class='warn'>Check for SA code {0} is missing</td></tr>",i);
					p("</tr>");
				}
			}
			
			Rule r = annotationHash.get(a);
			p("<tr>");
			p("<td>{0}</td>",a.sa() );				
			p("<td>{0}<br/><span class='author'>Author: {1}<br/>Date: {2}</span></td>", 
					toSafeHTML(a.desc()),
					toSafeHTML(a.author()),
					toSafeHTML(a.date())
			);			
			
			p("<td>Class: <tt>{0}</tt><br/>",r.method.getDeclaringClass().getSimpleName() );
			p("Method: <a href=\"source.php?c={0}&m={1}\" alt=\"Checked by {3}.{1}\"><tt>{1}</tt></a></td>",
					r.method.getDeclaringClass().getName(),
					r.method.getName(),
					r.method.getDeclaringClass().getName(),
					r.method.getDeclaringClass().getName() );
			p("</tr>");
			
			saNumber = a.sa();
		}		
		
		p("</table>");
		
		
		p("<h2>Completeness of SA checks</h2>");
	
		p("<table class='av'>");
		p(" <tr><th>Total SA Checks:</th><td>{0}</td></tr>",totalSA);
		p(" <tr><th>Implemented SA Checks:</th><td>{0}</td></tr>", (totalSA - missingSA)  );
		p(" <tr><th>Missing SA Checks:</th><td>{0}</td></tr>",missingSA );
		
		p(" <tr><th>% Complete:</th><td>{0,number,0.00}</td></tr>", 
				 100.0 * (totalSA - missingSA) /  totalSA );		
		p(" <tr><th>% TODO:</th><td>{0,number,0.00}</td></tr>", 100.0 * (missingSA) / totalSA);
		p("</table>");		
		
	}
	
	
	static void p (String msg, Object ... args ) {
		
		if (args.length == 0) {
			OUT.println(msg);
		} else {
			OUT.println(MessageFormat.format(msg, args));
		}
	}
	
	
	
	static String toSafeHTML ( String s ) {
		
		StringBuilder sb = new StringBuilder ( s.length() + s.length() / 4 ) ;
		
		for (char ch : s.toCharArray() ) {
			switch (ch) {
			case '<' : 
				sb.append("&lt;");
				break;
			case '&' :
				sb.append("&amp;");
				break;
			case '>' :
				sb.append("&gt;");
				break;
			default :
				sb.append(ch);
			}			
		}
		
		return sb.toString();		
		
	}
}

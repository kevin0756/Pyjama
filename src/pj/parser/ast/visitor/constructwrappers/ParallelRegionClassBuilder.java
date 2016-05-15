/*
 * Copyright (C) 2013-2016 Parallel and Reconfigurable Computing Group, University of Auckland.
 *
 * Authors: <http://homepages.engineering.auckland.ac.nz/~parallel/ParallelIT/People.html>
 * 
 * This file is part of Pyjama, a Java implementation of OpenMP-like directive-based 
 * parallelisation compiler and its runtime routines.
 *
 * Pyjama is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Pyjama is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Pyjama. If not, see <http://www.gnu.org/licenses/>.
 */

package pj.parser.ast.visitor.constructwrappers;
/**
 * This is the representation for <code>parallel</code>
 * construct.
 * 
 * It should be noted that this is an 
 * elementary directive. In scenarios, where
 * combined directives are used, they are again 
 * normalised into the elementary ones.
 * 
 * @author Xing Fan
 * @version 0.9
 */

import java.util.HashMap;
import java.util.List;
import pj.parser.ast.visitor.PyjamaToJavaVisitor;
import pj.parser.ast.omp.OmpDataClause;
import pj.parser.ast.omp.OmpParallelConstruct;
import pj.parser.ast.omp.OmpPrivateDataClause;
import pj.parser.ast.omp.OmpReductionDataClause;
import pj.parser.ast.omp.OmpSharedDataClause;
import pj.parser.ast.stmt.Statement;
import pj.parser.ast.visitor.SourcePrinter;
import pj.parser.ast.visitor.dataclausehandler.DataClausesHandler;

public class ParallelRegionClassBuilder extends ConstructWrapper  {
	
	private static HashMap<OmpParallelConstruct, ParallelRegionClassBuilder> pairs = new HashMap<OmpParallelConstruct, ParallelRegionClassBuilder>();

	public String className = "";
	
	/* This flag indicates whether this auxilary class has been printed once by the PyjamaToJava visitor, if has been printed,
	 * the getSource() method will return empty string. 
	 */
	private boolean hasPrinted = false;
	private SourcePrinter printer = new SourcePrinter();
	
	private String staticPrefix = "";
	
	public List<Statement> currentMethodOrConstructorStmts = null;

	public PyjamaToJavaVisitor visitor;
	public OmpParallelConstruct parallelConstruct;
	private List<OmpDataClause> dataClauseList;
	
	public static ParallelRegionClassBuilder create(OmpParallelConstruct parallelConstruct) {
		ParallelRegionClassBuilder prc = pairs.get(parallelConstruct);
		if (null == prc) {
			throw new RuntimeException("Try to create ParallelRegionClassBuilder from pre-visited parallelConstruct node, but node not found!");
		}
		return prc;
	}
	
	public static ParallelRegionClassBuilder create(OmpParallelConstruct parallelConstruct, 
			boolean isStatic, 
			PyjamaToJavaVisitor visitor,
			List<Statement> stmts) {
		ParallelRegionClassBuilder prc = pairs.get(parallelConstruct);
		if (null == prc) {
			prc = new ParallelRegionClassBuilder(parallelConstruct, isStatic, visitor, stmts);
			pairs.put(parallelConstruct, prc);
		}
		return prc;
	}
		
	private ParallelRegionClassBuilder(OmpParallelConstruct parallelConstruct, 
			boolean isStatic, 
			PyjamaToJavaVisitor visitor,
			List<Statement> stmts)
	{	
		this.parallelConstruct = parallelConstruct;
		this.dataClauseList = parallelConstruct.getDataClauseList();
		if (isStatic) {
			this.staticPrefix = "static ";
		}
		this.visitor = visitor;
		
		this.currentMethodOrConstructorStmts = stmts;
	}

	private Statement getUserCode() {
		return parallelConstruct.getBody();
	}

	@Override
	public int getBeginLine() {
		return parallelConstruct.getBeginLine();
	}
	
	@Override
	public int getEndLine() {
		return parallelConstruct.getEndLine();
	}
	

	public String get_inputlist() {
		return "inputlist_" + this.className;
	}
	
	public String get_outputlist() {
		return "outputlist_" + this.className;
	}
	
	public void setPrinterIndentLevel(int level) {
		this.printer.setIndentLevel(level);
	}
		
	public String getSource()
	{
		/*
		 * Ensure the auxilary class can only be printed once.
		 */
		if (this.hasPrinted) {
			return "";
		} else {
			this.generateClass();
			this.hasPrinted = true;
			return printer.getSource();
		}
	}
	
	private void generateClass() {
		printer.printLn();
		//////////////////////////////////////////////
		printer.printLn(this.staticPrefix +"class " + this.className + "{");
		printer.indent();
		printer.indent();
		printer.printLn("private int OMP_threadNumber = 1;");
		printer.printLn("private InternalControlVariables icv;");
		printer.printLn("private ConcurrentHashMap<String, Object> OMP_inputList = new ConcurrentHashMap<String, Object>();");
		printer.printLn("private ConcurrentHashMap<String, Object> OMP_outputList = new ConcurrentHashMap<String, Object>();");
		printer.printLn("private ReentrantLock OMP_lock;");
		printer.printLn("private ParIterator<?> OMP__ParIteratorCreator;");
		printer.printLn("public AtomicReference<Throwable> OMP_CurrentParallelRegionExceptionSlot = new AtomicReference<Throwable>(null);");
		printer.printLn();
		//#BEGIN shared variables defined here
		printer.printLn("//#BEGIN shared variables defined here");
		for(OmpDataClause sharedClause: this.dataClauseList) {
			if (sharedClause instanceof OmpSharedDataClause) {
				((OmpSharedDataClause) sharedClause).printSharedVariableDefination(parallelConstruct, printer);
			} else {
				continue;
			}
		}
		printer.printLn("//#END shared variables defined here");
		//#END shared variables defined here
		printer.printLn("public " + this.className + "(int thread_num, InternalControlVariables icv, ConcurrentHashMap<String, Object> inputlist, ConcurrentHashMap<String, Object> outputlist) {");
		printer.indent();
		printer.printLn("this.icv = icv;");
		printer.printLn("if ((false == Pyjama.omp_get_nested()) && (Pyjama.omp_get_level() > 0)) {");
		printer.indent();
		printer.printLn("this.OMP_threadNumber = 1;");
		printer.unindent();
		printer.printLn("}else {");
		printer.indent();
		printer.printLn("this.OMP_threadNumber = thread_num;");
		printer.unindent();
		printer.printLn("}");
		printer.printLn("this.OMP_inputList = inputlist;");
		printer.printLn("this.OMP_outputList = outputlist;");
		printer.printLn("icv.currentParallelRegionThreadNumber = this.OMP_threadNumber;");
		printer.printLn("icv.OMP_CurrentParallelRegionBarrier = new PjCyclicBarrier(this.OMP_threadNumber);");
		//#BEGIN shared variables initialised here
		printer.printLn("//#BEGIN shared variables initialised here");
		for(OmpDataClause sharedClause: this.dataClauseList) {
			if (sharedClause instanceof OmpSharedDataClause) {
				((OmpSharedDataClause) sharedClause).printSharedVariableInitialisation(parallelConstruct, printer);
			} else {
				continue;
			}
		}
		printer.printLn("//#END shared variables initialised here");
		//#END shared variables initialised here
		printer.unindent();
		printer.printLn("}");
		printer.printLn();
		printer.printLn("private void updateOutputListForSharedVars() {");
		printer.indent();
		//BEGIN put shared variables lastprivate(if any, though no available) to outputlist
		printer.printLn("//BEGIN update outputlist");
		DataClausesHandler.updateOutputlistForSharedVariablesInPRClass(this, printer);
		printer.printLn("//END update outputlist");
		//END put shared variables lastprivate(if any, though no available) to outputlist
		printer.unindent();
		printer.printLn("}");
		printer.printLn("class MyCallable implements Callable<Void> {");
		printer.indent();
		printer.printLn("private int alias_id;");
		printer.printLn("private ConcurrentHashMap<String, Object> OMP_inputList;");
		printer.printLn("private ConcurrentHashMap<String, Object> OMP_outputList;");
		
		//#BEGIN firstprivate reduction variables defined for each thread here
		printer.printLn("//#BEGIN private/firstprivate reduction variables defined here");
		for(OmpDataClause clause: this.dataClauseList) {
			if (clause instanceof OmpPrivateDataClause) {
				((OmpPrivateDataClause) clause).printPrivateVariableDefination(parallelConstruct, printer);
			} else if (clause instanceof OmpReductionDataClause) {
				((OmpReductionDataClause) clause).printReductionVariableDefination(parallelConstruct, printer);
			} else {
				continue;
			}
		}
		printer.printLn("//#END private/firstprivate reduction variables  defined here");
		//#END firstprivate reduction variables defined for each thread here
		
		printer.printLn("MyCallable(int id, ConcurrentHashMap<String,Object> inputlist, ConcurrentHashMap<String,Object> outputlist){");
		printer.indent();
		printer.printLn("this.alias_id = id;");
		printer.printLn("this.OMP_inputList = inputlist;");
		printer.printLn("this.OMP_outputList = outputlist;");
		
		//#BEGIN firstprivate reduction variables initialised for each thread here
		printer.printLn("//#BEGIN firstprivate reduction variables initialised here");
		for(OmpDataClause clause: this.dataClauseList) {
			if (clause instanceof OmpPrivateDataClause) {
				((OmpPrivateDataClause) clause).printPrivateVariableInitialisation(parallelConstruct, printer);
			} else if (clause instanceof OmpReductionDataClause) {
				((OmpReductionDataClause) clause).printReductionVariableInitialisation(parallelConstruct, printer);
			} else {
				continue;
			}
		}
		printer.printLn("//#END firstprivate reduction variables initialised here");
		//#END firstprivate reduction variables initialised for each thread here
		
		printer.unindent();
		printer.printLn("}");
		printer.printLn();
		printer.printLn("@Override");
		printer.printLn("public Void call() {");
		printer.indent();
		printer.printLn("try {");
		printer.indent();
		//BEGIN get construct user code
		printer.printLn("/****User Code BEGIN***/");
		this.getUserCode().accept(visitor, printer);
		printer.printLn();
		printer.printLn("/****User Code END***/");
		//BEGIN reduction procedure
		printer.printLn("//BEGIN reduction procedure");
		DataClausesHandler.reductionForPRClass(this, printer);
		printer.printLn("//END reduction procedure");
		//END reduction procedure
		printer.printLn("PjRuntime.setBarrier();");
		//BEGIN Master thread updateOutputList
		printer.unindent();
		printer.printLn("} catch (OmpParallelRegionLocalCancellationException e) {");
		printer.printLn(" 	PjRuntime.decreaseBarrierCount();");
		printer.printLn("} catch (Exception e) {");
		printer.printLn("    PjRuntime.decreaseBarrierCount();");
		printer.printLn("	PjExecutor.cancelCurrentThreadGroup();");
		printer.printLn("OMP_CurrentParallelRegionExceptionSlot.compareAndSet(null, e);");
		printer.unindent();
		printer.printLn("}");
		printer.printLn("if (0 == this.alias_id) {");
		printer.indent();
		printer.printLn("updateOutputListForSharedVars();");
		if (this.parallelConstruct.isFreegui()) {
		    /*
		     * if current parallel region has freegui attribute, master
		     * thread also should do invoke remaining code in current method.
		     */
		    printer.printLn(this.generateDummyGuiCode());
		}
		printer.unindent();
		printer.printLn("}");
		//END Master thread updateOutputList
		//END get construct user code
		printer.printLn("return null;");
		printer.unindent();
		printer.printLn("}");
		printer.unindent();
		printer.printLn("}");
		printer.printLn("public void runParallelCode() {");
		printer.indent();
		this.generateRunnable();
		printer.unindent();
		printer.printLn("}");
		printer.unindent();
		printer.printLn("}");
		
	}	
	
	private void generateRunnable() {
		/*
		 * If current directive is //#omp freeguithread, free edt thread and make another more thread
		 * to substitute edt thread.
		 */
		if (this.parallelConstruct.isFreegui()) {
			printer.printLn("for (int i = 0; i <= this.OMP_threadNumber-1; i++) {");
			printer.indent();
			printer.printLn("Callable<ConcurrentHashMap<String,Object>> slaveThread = new MyCallable(i, OMP_inputList, OMP_outputList);");
			printer.printLn("PjRuntime.submit(i, slaveThread, icv);");
			printer.unindent();
			printer.printLn("}");
		}
		/*
		 * else the current directive is //#omp parallel, master thread is current thread, doesn't escape
		 * from parallel region.
		 */
		else {
			printer.printLn("for (int i = 1; i <= this.OMP_threadNumber-1; i++) {");
			printer.indent();
			printer.printLn("Callable<Void> slaveThread = new MyCallable(i, OMP_inputList, OMP_outputList);");
			printer.printLn("PjRuntime.submit(i, slaveThread, icv);");
			printer.unindent();
			printer.printLn("}");
			printer.printLn("Callable<Void> masterThread = new MyCallable(0, OMP_inputList, OMP_outputList);");
			printer.printLn("PjRuntime.getCurrentThreadICV().currentThreadAliasID = 0;");
			printer.printLn("try {");
			printer.indent();
			printer.printLn("masterThread.call();");
			printer.unindent();
			printer.printLn("} catch (Exception e) {");
			printer.indent();
			printer.printLn("e.printStackTrace();");
			printer.unindent();
			printer.printLn("}");
		}
	}
	
	private String generateDummyGuiCode() {
		GuiCodeClassBuilder currentGuiCode = DataClausesHandler.generateDummyGuiRegionForNoguiRemainingCode(this);
		String returnCode = currentGuiCode.getSource();
		return returnCode;
	}
}

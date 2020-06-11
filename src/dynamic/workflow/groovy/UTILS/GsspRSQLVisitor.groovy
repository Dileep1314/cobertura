package groovy.UTILS


import cz.jirutka.rsql.parser.ast.AndNode
import cz.jirutka.rsql.parser.ast.ComparisonNode
import cz.jirutka.rsql.parser.ast.OrNode
import cz.jirutka.rsql.parser.ast.RSQLVisitor
import cz.jirutka.rsql.parser.ast.Node

public class GsspRSQLVisitor implements RSQLVisitor {

	
	HashMap<String,String> paramMap=new HashMap<String,String>();
	
	@Override
	public Object visit(AndNode node, Object param) {
		for(Node n:node.getChildren())
		{
			n.accept(this)
		}
		return node
	}

	@Override
	public Object visit(OrNode node, Object param) {
		for(Node n:node.getChildren())
		{
			n.accept(this)
		}
		return node
	}

	@Override
	public Object visit(ComparisonNode node, Object param) {
		paramMap.putAt(node.getSelector(),node.getArguments().iterator().join(","))
		return node
	}
	
	public def getMap()
	{
		return paramMap
	}


}
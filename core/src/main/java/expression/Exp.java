package expression;

import expression.types.Types;

import java.util.*;

/**
 * Can your application be structured in such a way that both the data model and the set
 * of virtual operations over it can be extended without the need to modify existing code,
 * without the need for code repetition and without runtime type errors.”
 *
 * http://www.scala-lang.org/docu/files/TheExpressionProblem.pdf
 *
 * http://i.cs.hku.hk/~bruno/papers/Modularity2016.pdf
 *
 * This is the high-level algebraic data type
 */
public class Exp {

    /** Represents table of operations. */
    public List<Method> ops = new ArrayList<>();

    public Exp() {
        // default is to have an eval operation with no arguments and return type int.
        ops.add(new FunctionMethod("eval", Types.Int));
    }

}

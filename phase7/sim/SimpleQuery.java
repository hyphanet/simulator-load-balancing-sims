package sim;

class SimpleQuery {

    public static final int SUCCESS=0;
    public static final int DEADEND=1;
    public static final int HTLSTOP=2;
    public static final int REJECTLOOP=3;
    public static final int FORWARD=4;

    final SimpleNode source;
    final SimpleNode dest;
    final SimpleNode target;
    final int type;
    int htl;

    public SimpleQuery (SimpleNode source, SimpleNode dest, SimpleNode target, int type, int htl) {
	this.source = source;
	this.dest = dest;
	this.target = target;
	this.type = type;
	this.htl = htl;
    }

}

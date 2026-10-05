import com.phoenix.agent.util.SessionFileTree;
import com.phoenix.agent.util.SessionFileTree.Attribution;

public class TreeProbe {
    static int fail = 0, pass = 0;
    static final String SID = "6e9c09e0-5459-4904-9087-557501fb724a";
    static final String AK = "agent-33";

    static void ck(String label, String key, Attribution wantAttr, String wantRel, long wantSize, boolean wantInternal) {
        SessionFileTree.Parsed p = SessionFileTree.parse(key, AK, SID);
        boolean ok = p.attribution() == wantAttr
                && (wantRel == null || p.relativePath().equals(wantRel))
                && p.sizeBytes() == wantSize
                && p.internal() == wantInternal;
        if (ok) pass++; else fail++;
        System.out.printf("%s %-26s attr=%-7s rel=%-42s size=%-5d internal=%-5s%n",
                ok ? "PASS" : "FAIL", label, p.attribution(), p.relativePath(), p.sizeBytes(), p.internal());
    }

    public static void main(String[] a) {
        ck("三代-会话根文件", AK + "/" + SID + "/sh_probe.txt:120", Attribution.SESSION, "sh_probe.txt", 120, false);
        ck("三代-uid嵌套", AK + "/" + SID + "/461671714812850176/a/b.svg:1234", Attribution.SESSION, "461671714812850176/a/b.svg", 1234, false);
        ck("三代-噪音.pylibs", AK + "/" + SID + "/.pylibs/pandas/core.py:99", Attribution.SESSION, ".pylibs/pandas/core.py", 99, true);
        ck("三代-call_占位", AK + "/" + SID + "/large_tool_results/call_abc.txt:10", Attribution.SESSION, "large_tool_results/call_abc.txt", 10, true);
        ck("二代-历史", AK + "/461671714812850176/legacy.txt:55", Attribution.HISTORY, "461671714812850176/legacy.txt", 55, false);
        ck("一代-历史", "461671714812850176/old.txt:7", Attribution.HISTORY, "461671714812850176/old.txt", 7, false);
        ck("他会话-隔离", AK + "/11111111-2222-3333-4444-555555555555/x.txt:1", Attribution.HISTORY, null, 1, false);
        ck("无size后缀", AK + "/" + SID + "/plain.txt", Attribution.SESSION, "plain.txt", -1, false);
        ck("前缀不匹配但含本会话", "owl-kids/" + SID + "/a.md:5", Attribution.SESSION, "a.md", 5, false);
        ck("内部状态文件", AK + "/" + SID + "/MEMORY.md:3", Attribution.SESSION, "MEMORY.md", 3, true);
        ck("内部后缀jsonl", AK + "/" + SID + "/notes.jsonl:2", Attribution.SESSION, "notes.jsonl", 2, true);
        ck("空键-不抛", "", Attribution.HISTORY, "", -1, false);
        ck("他会话在首位-隔离", "11111111-2222-3333-4444-555555555555/y.txt:9", Attribution.HISTORY, "11111111-2222-3333-4444-555555555555/y.txt", 9, false);
        System.out.println("---- PASS=" + pass + " FAIL=" + fail + " ----");
        if (fail > 0) System.exit(1);
    }
}

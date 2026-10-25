package com.daimaobatiao.pet;

import java.util.Random;

// 台词，和 Mac / Windows 版一致
final class Lines {
    static final String[] GREET = {"咪～八条上岗了。", "来啦？就知道你会想咪。"};
    static final String[] PET = {"再摸一下，就一下。", "嗯…允许你多摸三秒。", "咪才没有觉得很舒服。", "左边也要摸。"};
    static final String[] PET_TOO_MUCH = {"摸够了没！", "再摸就收费了，一下一根小鱼干。", "咪的毛要被你摸秃了！"};
    static final String[] FEED = {"小鱼干！你是好人。", "吃完再说，别打扰咪。", "就这点？不够塞牙缝的。"};
    static final String[] DRAG = {"放咪下来！", "咪恐高！", "要去哪儿？"};
    static final String[] LAND = {"稳稳落地。", "哼，下次轻点。"};
    static final String[] WALK = {"巡逻中。", "咪去那边看看。", "散步时间。"};
    static final String[] IDLE = {"盯——", "咪在摸鱼，你也摸会儿？", "你在忙啥？带咪一个。", "咪饿了，只是说说。"};
    static final String[] SLEEP = {"困了，别吵。", "Zzz…", "咪先睡一会儿。"};
    static final String[] WAKE = {"谁叫咪？", "咪没睡，咪在思考。"};
    static final String[] REMIND = {"坐太久啦，起来伸个懒腰。", "喝口水，咪看着你喝。", "眼睛累了吧？看看远处。"};

    private static final Random RND = new Random();

    static String any(String[] a) {
        return a[RND.nextInt(a.length)];
    }
}

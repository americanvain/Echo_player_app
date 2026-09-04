#!/usr/bin/env python3
"""生成 app/src/main/assets/materials/*.json —— 随 APK 内置的阅读资源。

格式与服务器流水线 GET /materials/{id} 的输出一致（docs/SERVER_API.md），
所以将来把这些资源挪到服务器上时客户端不用改。

用法：python3 tools/build_bundled_materials.py [speech_evaluating/data/articles.json]
"""
import json
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "materials")


def material(id, title, title_zh, description, description_zh, level, topic, source, segments):
    return {
        "id": id, "title": title, "title_zh": title_zh,
        "description": description, "description_zh": description_zh,
        "level": level, "topic": topic, "language": "en", "source": source,
        "segments": [{"source_ref": ref, "units": [{"text": t, "translation": z} for t, z in units]} for ref, units in segments],
    }


MATERIALS = []

# ---------------------------------------------------------------- A1 对话
MATERIALS.append(material(
    "coffee-shop", "At the Coffee Shop", "在咖啡店",
    "A short everyday conversation: ordering a drink and making small talk.",
    "一段日常对话：点一杯饮料，随便聊几句。", "A1", "生活", "Echo Player original",
    [("coffee-shop.txt", [
        ("Good morning! What can I get for you today?", "早上好！今天想喝点什么？"),
        ("Hi. Could I have a medium latte, please?", "你好。请给我一杯中杯拿铁。"),
        ("Sure. Would you like it hot or iced?", "好的。要热的还是冰的？"),
        ("Hot, please. It's pretty cold outside this morning.", "热的，谢谢。今天早上外面挺冷的。"),
        ("I know! Winter came early this year.", "是啊！今年冬天来得早。"),
        ("Do you want anything to eat with that?", "要配点吃的吗？"),
        ("Hmm, what do you recommend?", "嗯，你推荐什么？"),
        ("The blueberry muffins just came out of the oven.", "蓝莓玛芬刚刚出炉。"),
        ("Perfect, I'll take one of those.", "太好了，我要一个。"),
        ("That'll be six dollars and fifty cents.", "一共六美元五十美分。"),
        ("Can I pay by card?", "可以刷卡吗？"),
        ("Of course. Just tap it here.", "当然可以。在这里碰一下就行。"),
        ("Thanks. Have a nice day!", "谢谢。祝你今天愉快！"),
        ("You too. Your order will be ready in a minute.", "你也是。你的饮料马上就好。"),
    ])],
))

# ---------------------------------------------------------------- A1 寓言
MATERIALS.append(material(
    "aesop-fox-grapes", "The Fox and the Grapes", "狐狸与葡萄",
    "The fable behind the phrase \"sour grapes\", retold in simple English.",
    "“酸葡萄”这个说法的来源，用简单英语重述。", "A1", "寓言", "Aesop's Fables (public domain), retold",
    [("aesop.txt", [
        ("One hot afternoon, a fox was walking through a vineyard.", "一个炎热的下午，一只狐狸走过一片葡萄园。"),
        ("High above his head, he saw a bunch of ripe, purple grapes.", "在头顶高处，他看见一串熟透的紫葡萄。"),
        ("\"Those look delicious,\" he said. \"Just what I need on a day like this.\"", "“看起来真好吃，”他说，“这样的天气正需要这个。”"),
        ("He jumped as high as he could, but he missed them.", "他使劲往上跳，却没够着。"),
        ("He stepped back, ran, and jumped again.", "他退后几步，助跑，再跳一次。"),
        ("Again and again he tried, and again and again he failed.", "他试了一次又一次，也失败了一次又一次。"),
        ("At last, tired and hot, he gave up.", "最后，他又累又热，放弃了。"),
        ("As he walked away, he lifted his nose and said, \"They were probably sour anyway.\"", "走开的时候，他扬起鼻子说：“反正那些葡萄大概是酸的。”"),
        ("It is easy to hate what you cannot have.", "得不到的东西，说它不好总是容易的。"),
    ])],
))

# ---------------------------------------------------------------- A2 寓言
MATERIALS.append(material(
    "aesop-north-wind", "The North Wind and the Sun", "北风与太阳",
    "A classic fable about persuasion and force.",
    "关于“说服”与“强迫”的经典寓言。", "A2", "寓言", "Aesop's Fables (public domain), retold",
    [("aesop.txt", [
        ("The North Wind and the Sun were arguing about which of them was stronger.", "北风和太阳争论谁更强。"),
        ("While they argued, a traveler came down the road, wrapped in a warm cloak.", "他们争着争着，一个裹着厚斗篷的旅人从路上走来。"),
        ("\"Let's settle this,\" said the Sun. \"Whoever can make that man take off his cloak is the stronger.\"", "“咱们这样定，”太阳说，“谁能让那个人脱下斗篷，谁就更强。”"),
        ("The North Wind went first.", "北风先来。"),
        ("He blew as hard as he could, and the cold air rushed down the road.", "他用尽全力地吹，寒风顺着道路呼啸而下。"),
        ("But the harder he blew, the tighter the traveler held his cloak around him.", "可他吹得越猛，旅人就把斗篷裹得越紧。"),
        ("Finally, the North Wind gave up.", "最后，北风放弃了。"),
        ("Then the Sun came out and shone gently on the traveler.", "接着太阳出来了，温和地照在旅人身上。"),
        ("Soon the man felt warm, and he loosened his cloak.", "很快旅人觉得暖和了，松开了斗篷。"),
        ("A little later, he took it off completely and sat down in the shade to rest.", "又过了一会儿，他干脆把斗篷脱下来，坐到树荫下休息。"),
        ("So the Sun won, and the North Wind had to admit it.", "于是太阳赢了，北风只好认输。"),
        ("Gentleness often works better than force.", "温和常常比强硬更有效。"),
    ])],
))

MATERIALS.append(material(
    "aesop-tortoise-hare", "The Tortoise and the Hare", "龟兔赛跑",
    "Slow and steady wins the race.",
    "稳扎稳打，方能取胜。", "A2", "寓言", "Aesop's Fables (public domain), retold",
    [("aesop.txt", [
        ("A hare was making fun of a tortoise for being so slow.", "一只兔子嘲笑乌龟走得太慢。"),
        ("\"Do you ever get anywhere?\" he asked with a laugh.", "“你到底能不能走到哪儿去啊？”他笑着问。"),
        ("\"Yes,\" replied the tortoise, \"and I get there sooner than you think.\"", "“能，”乌龟回答，“而且比你想的更快。”"),
        ("\"Let's race and see,\" said the hare.", "“那咱们比一场看看，”兔子说。"),
        ("The fox agreed to be the judge, and the race began.", "狐狸同意当裁判，比赛开始了。"),
        ("The hare ran so fast that he was soon far ahead.", "兔子跑得飞快，很快就遥遥领先。"),
        ("\"This is too easy,\" he thought. \"I have time for a nap.\"", "“这也太容易了，”他想，“我还有时间睡一觉。”"),
        ("So he lay down under a tree and fell asleep.", "于是他躺在一棵树下睡着了。"),
        ("Meanwhile, the tortoise kept walking, slowly but without stopping.", "与此同时，乌龟一直在走，慢，但从不停下。"),
        ("When the hare woke up, the tortoise was already near the finish line.", "兔子醒来时，乌龟已经快到终点了。"),
        ("The hare ran as fast as he could, but it was too late.", "兔子拼命地跑，可已经来不及了。"),
        ("The tortoise had won.", "乌龟赢了。"),
        ("Slow and steady wins the race.", "稳扎稳打，方能取胜。"),
    ])],
))

# ---------------------------------------------------------------- B1 科普
MATERIALS.append(material(
    "why-sky-blue", "Why Is the Sky Blue?", "天空为什么是蓝色的？",
    "A short science explainer with a few technical words worth learning.",
    "一篇简短的科普，有几个值得学的术语。", "B1", "科学", "Echo Player original",
    [("science.txt", [
        ("Have you ever wondered why the sky is blue during the day but red or orange at sunset?", "你有没有想过，为什么白天天空是蓝色的，日落时却变成红色或橙色？"),
        ("The answer has to do with sunlight and the air itself.", "答案与阳光和空气本身有关。"),
        ("Sunlight looks white, but it is actually made up of all the colors of the rainbow.", "阳光看起来是白色的，其实由彩虹的所有颜色组成。"),
        ("Each color travels as a wave, and each wave has a different length.", "每种颜色以波的形式传播，波长各不相同。"),
        ("Blue light has a short wavelength, while red light has a long one.", "蓝光波长短，红光波长长。"),
        ("When sunlight enters the atmosphere, it bumps into tiny molecules of gas.", "阳光进入大气层时，会撞上微小的气体分子。"),
        ("These molecules scatter short waves much more than long ones.", "这些分子对短波的散射远比长波强。"),
        ("So blue light gets scattered in every direction, and that is what we see when we look up.", "所以蓝光被散射到四面八方，我们抬头看到的就是它。"),
        ("At sunset, the light has to travel through much more air to reach your eyes.", "日落时，光线要穿过厚得多的空气才能到达你的眼睛。"),
        ("By then, most of the blue has been scattered away.", "到那时，大部分蓝光已经被散射掉了。"),
        ("What is left is the red and orange light, which paints the evening sky.", "剩下的是红光和橙光，它们把傍晚的天空染上颜色。"),
        ("This effect is called Rayleigh scattering, named after the scientist who explained it.", "这种现象叫瑞利散射，以解释它的科学家命名。"),
    ])],
))

# ---------------------------------------------------------------- B2 文学
MATERIALS.append(material(
    "gift-of-the-magi", "The Gift of the Magi (opening)", "麦琪的礼物（开篇）",
    "The famous opening of O. Henry's 1905 short story. Long sentences, rich vocabulary.",
    "欧·亨利 1905 年名篇的开头。长句多，词汇丰富。", "B2", "文学", "O. Henry, 1905 (public domain)",
    [("magi.txt", [
        ("One dollar and eighty-seven cents.", "一块八毛七。"),
        ("That was all.", "就这么多。"),
        ("And sixty cents of it was in pennies.", "其中六毛还是一分一分的硬币。"),
        ("Pennies saved one and two at a time by bulldozing the grocer and the vegetable man and the butcher until one's cheeks burned with the silent imputation of parsimony that such close dealing implied.", "这些硬币是一分两分地攒下来的，靠的是跟杂货商、菜贩和肉贩死磨硬泡，磨到自己脸上发烫——这样斤斤计较，分明是在无声地被人说小气。"),
        ("Three times Della counted it.", "德拉数了三遍。"),
        ("One dollar and eighty-seven cents.", "一块八毛七。"),
        ("And the next day would be Christmas.", "而第二天就是圣诞节了。"),
        ("There was clearly nothing to do but flop down on the shabby little couch and howl.", "显然，除了一头扑到那张破旧的小沙发上放声大哭，再没别的办法。"),
        ("So Della did it.", "德拉就这么做了。"),
        ("Which instigates the moral reflection that life is made up of sobs, sniffles, and smiles, with sniffles predominating.", "这不禁让人生出一番感慨：人生是由啜泣、抽噎和微笑组成的，而抽噎占了大头。"),
        ("While the mistress of the home is gradually subsiding from the first stage to the second, take a look at the home.", "趁这位家庭主妇从第一阶段慢慢过渡到第二阶段的工夫，我们来看看这个家。"),
        ("A furnished flat at $8 per week.", "一套每周八美元的带家具公寓。"),
        ("It did not exactly beggar description, but it certainly had that word on the lookout for the mendicancy squad.", "倒也不至于穷得无法形容，但“穷”这个字眼确实正提防着乞丐纠察队找上门来。"),
    ])],
))

# ---------------------------------------------------------------- C1 演讲
MATERIALS.append(material(
    "gettysburg", "The Gettysburg Address", "葛底斯堡演说",
    "Lincoln's 1863 speech: ten sentences, some of them very long. Good practice for syntax.",
    "林肯 1863 年的演说：十句话，有几句非常长，适合练句法。", "C1", "演讲", "Abraham Lincoln, 1863 (public domain)",
    [("gettysburg.txt", [
        ("Four score and seven years ago our fathers brought forth on this continent, a new nation, conceived in Liberty, and dedicated to the proposition that all men are created equal.", "八十七年前，我们的先辈在这块大陆上创立了一个新国家，它孕育于自由之中，奉行人人生而平等的原则。"),
        ("Now we are engaged in a great civil war, testing whether that nation, or any nation so conceived and so dedicated, can long endure.", "如今我们正在进行一场伟大的内战，以考验这个国家，或者任何一个如此孕育、如此奉行原则的国家，能否长久存在。"),
        ("We are met on a great battle-field of that war.", "我们相聚在这场战争的一个伟大战场上。"),
        ("We have come to dedicate a portion of that field, as a final resting place for those who here gave their lives that that nation might live.", "我们来到这里，是要把这战场的一部分奉献给那些为了国家的生存而在此献出生命的人，作为他们最后的安息之所。"),
        ("It is altogether fitting and proper that we should do this.", "我们这样做是完全应该而且非常恰当的。"),
        ("But, in a larger sense, we can not dedicate, we can not consecrate, we can not hallow this ground.", "但是，从更广的意义上说，这块土地我们不能够奉献，不能够圣化，不能够神化。"),
        ("The brave men, living and dead, who struggled here, have consecrated it, far above our poor power to add or detract.", "那些曾在这里战斗过的勇士们，活着的和死去的，已经把这块土地圣化了，这远不是我们微薄的力量所能增减的。"),
        ("The world will little note, nor long remember what we say here, but it can never forget what they did here.", "世人不会注意，也不会长久记住我们在这里说的话，但永远不会忘记他们在这里做过的事。"),
        ("It is for us the living, rather, to be dedicated here to the unfinished work which they who fought here have thus far so nobly advanced.", "毋宁说，我们这些活着的人，应该在这里把自己奉献给那些曾在此战斗的人已经如此崇高地推进了的未竟事业。"),
        ("It is rather for us to be here dedicated to the great task remaining before us, that from these honored dead we take increased devotion to that cause for which they gave the last full measure of devotion, that we here highly resolve that these dead shall not have died in vain, that this nation, under God, shall have a new birth of freedom, and that government of the people, by the people, for the people, shall not perish from the earth.", "我们更应该在这里献身于摆在我们面前的伟大任务：从这些光荣的死者身上汲取更多的献身精神，去完成他们已为之献出全部忠诚的事业；我们在此下定决心，不让这些死者白白牺牲；让这个国家在上帝庇佑下获得自由的新生；让民有、民治、民享的政府永世长存。"),
    ])],
))


def convert_articles(path):
    """speech_evaluating/data/articles.json → 同一格式（一段一句）。"""
    with open(path, encoding="utf-8") as f:
        articles = json.load(f)
    out = []
    for a in articles:
        out.append(material(
            a["id"], a["title"], a.get("title_zh"), a.get("description"), a.get("description_zh"),
            a.get("level"), a.get("topic"), "speecheval articles",
            [(a["id"] + ".txt", [(p["text"], p.get("translation")) for p in a["paragraphs"]])],
        ))
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    mats = list(MATERIALS)
    if len(sys.argv) > 1 and os.path.exists(sys.argv[1]):
        mats = convert_articles(sys.argv[1]) + mats
    order = ["coffee-shop", "small-habits", "aesop-fox-grapes", "morning-market", "aesop-north-wind", "aesop-tortoise-hare",
             "first-presentation", "why-sky-blue", "gift-of-the-magi", "gettysburg"]
    by_id = {m["id"]: m for m in mats}
    names = []
    for mid in order + [m for m in by_id if m not in order]:
        m = by_id.get(mid)
        if not m:
            continue
        name = f"{mid}.json"
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            json.dump(m, f, ensure_ascii=False, indent=2)
        names.append(name)
        n = sum(len(s["units"]) for s in m["segments"])
        print(f"{name:32s} {m['level']:3s} {n:3d} units")
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump(names, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()

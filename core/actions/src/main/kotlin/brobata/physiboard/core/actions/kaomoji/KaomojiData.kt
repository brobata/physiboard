package brobata.physiboard.core.actions.kaomoji

/**
 * PhysiBoard's kaomoji collection, written for this project (spec: expansion-clipboard-pickers-
 * launcher.md SS4.9). One line per kaomoji: `kaomoji  ::  name  ::  tag, tag, ...`. The kaomoji is
 * inserted exactly as written between the start of the line and the first separator (outer spaces
 * trimmed). Keep each kaomoji in one group only; a test holds that.
 */
object KaomojiData {
    class Source(val id: String, val label: String, val tabLabel: String, val body: String)

    val GROUPS: List<Source> = listOf(
        Source(
            "JOY", "Joy", "^‿^",
            """
(＾▽＾)  ::  happy  ::  joy, smile, glad
(^_^)  ::  smile  ::  happy, joy, content
(^‿^)  ::  gentle smile  ::  happy, joy
(≧▽≦)  ::  overjoyed  ::  excited, happy, yay
(✿◠‿◠)  ::  flower smile  ::  happy, cute, joy
(◕‿◕)  ::  bright smile  ::  happy, cute
(｡◕‿◕｡)  ::  cute smile  ::  happy, joy
ヽ(・∀・)ﾉ  ::  cheering  ::  yay, happy, excited
\(^o^)/  ::  hooray  ::  yay, cheer, happy, celebrate
＼(＾▽＾)／  ::  celebrate  ::  hooray, yay, joy, party
٩(◕‿◕)۶  ::  delighted  ::  happy, joy, yay
(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧  ::  sparkle joy  ::  excited, magic, happy, yay
(☆▽☆)  ::  starry eyed  ::  excited, amazed, happy
(*^▽^*)  ::  blushing joy  ::  happy, smile
(⌒‿⌒)  ::  content  ::  happy, calm, smile
(o^▽^o)  ::  cheerful  ::  happy, joy
(￣▽￣)  ::  smug grin  ::  grin, smirk, pleased
(^ω^)  ::  pleased  ::  happy, smile
ヾ(≧▽≦*)o  ::  thrilled  ::  excited, yay, happy
(ﾉ´ヮ`)ﾉ*: ･ﾟ  ::  sprinkle joy  ::  happy, magic, excited
o(≧▽≦)o  ::  squee  ::  excited, happy, yay
(°▽°)  ::  grinning  ::  happy, grin
(^∇^)  ::  laughing  ::  laugh, happy
(≧∀≦)  ::  laugh out loud  ::  lol, laugh, happy
(ʘ‿ʘ)  ::  wide eyed smile  ::  happy, smile
( ˘▽˘)っ♨  ::  cozy  ::  warm, happy, relax, tea
☆*:.｡.o(≧▽≦)o.｡.:*☆  ::  super happy  ::  excited, yay, stars, joy
(•‿•)  ::  simple smile  ::  happy, smile
^_^  ::  classic smile  ::  happy, smile, joy
^^  ::  tiny smile  ::  happy, smile
""",
        ),
        Source(
            "LOVE", "Love", "♡",
            """
(♥ω♥*)  ::  heart eyes  ::  love, crush, adore
(´∀｀)♡  ::  lovestruck  ::  love, heart
(灬♥ω♥灬)  ::  blushing love  ::  love, crush, shy
(｡♥‿♥｡)  ::  in love  ::  love, heart eyes, crush
(◍•ᴗ•◍)❤  ::  sending love  ::  love, heart
(っ´▽｀)っ♡  ::  love hug  ::  hug, love
(づ｡◕‿‿◕｡)づ  ::  hug  ::  hug, cuddle, love
(つ≧▽≦)つ  ::  big hug  ::  hug, excited
⊂(･ω･*⊂)  ::  reaching hug  ::  hug, cuddle
(*˘︶˘*).｡.:*♡  ::  dreamy love  ::  love, dreamy, heart
(´｡• ω •｡`) ♡  ::  shy love  ::  love, shy, cute
(ɔˆ ³(ˆ⌣ˆc)  ::  kiss  ::  kiss, love, couple
(˘³˘)♥  ::  blowing a kiss  ::  kiss, love
( ˘ ³˘)♥  ::  kissy face  ::  kiss, love, mwah
(*^3^)/~☆  ::  air kiss  ::  kiss, love, mwah
(♡°▽°♡)  ::  adoring  ::  love, heart eyes
♡＼(￣▽￣)／♡  ::  love everyone  ::  love, hooray, hearts
(●´□`)♡  ::  swooning  ::  love, swoon
(´ ε ` )♡  ::  smooch  ::  kiss, love
(✿ ♥‿♥)  ::  flower love  ::  love, cute
(｡・//ε//・｡)  ::  shy kiss  ::  kiss, shy, blush
<3  ::  heart  ::  love
(人 •͈ᴗ•͈)  ::  grateful  ::  thanks, love, please
(っ˘з(˘⌣˘ )  ::  cheek kiss  ::  kiss, love, couple
(´,,•ω•,,)♡  ::  cute love  ::  love, blush
♥‿♥  ::  heart eyes simple  ::  love
(*♡∀♡)  ::  lovesick  ::  love, crush
(っ◔◡◔)っ ♥  ::  here have a heart  ::  give, love, gift
""",
        ),
        Source(
            "EMBARRASSED", "Embarrassed", "^^;",
            """
(⁄ ⁄•⁄ω⁄•⁄ ⁄)  ::  blushing  ::  shy, embarrassed, blush
(〃▽〃)  ::  bashful  ::  shy, blush
(*ﾉωﾉ)  ::  hiding face  ::  shy, embarrassed
(＞﹏＜)  ::  cringe  ::  embarrassed, ugh
(^_^;)  ::  nervous laugh  ::  sweat, awkward, embarrassed
(^^;)  ::  awkward smile  ::  sweat, nervous
(・_・;)  ::  uneasy  ::  nervous, sweat, awkward
(￣▽￣;)  ::  sheepish  ::  awkward, nervous
(⌒_⌒;)  ::  embarrassed smile  ::  awkward, blush
(〃＞＿＜;〃)  ::  flustered  ::  embarrassed, shy
(*/ω＼)  ::  peeking  ::  shy, hide, embarrassed
(///▽///)  ::  very red  ::  blush, embarrassed, shy
(o_ _)o  ::  bowing in shame  ::  sorry, embarrassed, apology
m(_ _)m  ::  deep bow  ::  sorry, apology, please, thanks
(シ_ _)シ  ::  apologizing  ::  sorry, apology, bow
(；￣Д￣)  ::  sweating  ::  nervous, awkward
(゜▽゜;)  ::  forced smile  ::  nervous, awkward
(￣ヘ￣;)  ::  troubled  ::  awkward, worried
(^～^;)ゞ  ::  scratching head  ::  awkward, oops
(¬‿¬ )  ::  sly  ::  smirk, mischievous
(-‸ლ)  ::  facepalm  ::  ugh, smh, embarrassed
(－‸ლ)  ::  face palm  ::  facepalm, ugh, smh
""",
        ),
        Source(
            "SAD", "Sad", "T_T",
            """
(╥﹏╥)  ::  crying  ::  sad, tears, cry
(T_T)  ::  tears  ::  cry, sad
T_T  ::  crying simple  ::  cry, sad, tears
(ಥ﹏ಥ)  ::  sobbing  ::  cry, sad, tears
(｡•́︿•̀｡)  ::  pouty sad  ::  sad, upset, sulk
(っ˘̩╭╮˘̩)っ  ::  comfort me  ::  sad, hug, cry
(´；ω；`)  ::  weeping  ::  cry, sad
(︶︹︺)  ::  gloomy  ::  sad, upset
(-_-)  ::  deadpan  ::  unamused, meh
(._.)  ::  down  ::  sad, quiet, meh
(；＿；)  ::  teary  ::  cry, sad
(´._.`)  ::  dejected  ::  sad, down
(ノ_<。)  ::  wiping tears  ::  cry, sad
(ToT)  ::  bawling  ::  cry, sad, tears
(ㄒoㄒ)  ::  wailing  ::  cry, sad, tears
(｡╯︵╰｡)  ::  heartbroken  ::  sad, upset
(πーπ)  ::  teary eyed  ::  cry, sad
(_ _|||)  ::  depressed  ::  sad, gloom
(╯︵╰,)  ::  sniffle  ::  sad, cry
(っ- ‸ – ς)  ::  disappointed  ::  sad, letdown
:'(  ::  crying emoticon  ::  cry, sad, tears
:(  ::  frown  ::  sad, unhappy
(´・ω・`)  ::  dismayed  ::  sad, oh no, shobon
(︶︿︶)  ::  sulking  ::  sad, pout, upset
(ಡ‸ಡ)  ::  holding back tears  ::  sad, cry
""",
        ),
        Source(
            "ANGRY", "Angry", "ಠ_ಠ",
            """
ಠ_ಠ  ::  look of disapproval  ::  disapprove, stare, angry, judging
(╬ Ò﹏Ó)  ::  furious  ::  angry, rage, mad
(＃｀Д´)  ::  shouting  ::  angry, mad, rage
(ノಠ益ಠ)ノ  ::  raging  ::  angry, rage, furious
(｀ε´)  ::  grumpy  ::  angry, pout, sulk
(눈_눈)  ::  suspicious glare  ::  angry, side eye, suspicious
(≖_≖ )  ::  side eye  ::  suspicious, skeptical
ヽ(`Д´)ﾉ  ::  tantrum  ::  angry, mad, rage
(╯°益°)╯  ::  seething  ::  angry, rage
(¬_¬)  ::  unimpressed  ::  annoyed, side eye, meh
(；一_一)  ::  annoyed  ::  irritated, meh
(•̀ᴗ•́)و  ::  determined  ::  fight, yes, motivated
(ง •̀_•́)ง  ::  ready to fight  ::  fight, angry, punch
(ง'̀-'́)ง  ::  put em up  ::  fight, boxing
(҂`з´)  ::  menacing  ::  angry, threat
(▼皿▼#)  ::  evil rage  ::  angry, furious, evil
(￣^￣)  ::  hmph  ::  pout, offended, snob
(｀へ´)  ::  pouting  ::  angry, pout, sulk
(ꐦ°᷄д°᷅)  ::  fuming  ::  angry, mad
(｀Д´)  ::  yelling  ::  angry, mad
>:(  ::  angry emoticon  ::  angry, mad
(ಠ益ಠ)  ::  livid  ::  angry, rage
(¬､¬)  ::  jealous  ::  envy, annoyed
凸(￣ヘ￣)  ::  rude gesture  ::  angry, middle finger
(-_-メ)  ::  irked  ::  annoyed, angry
""",
        ),
        Source(
            "SURPRISE", "Surprise", "O_O",
            """
(⊙_⊙)  ::  wide eyed  ::  surprised, shocked, stare
(°o°)  ::  gasp  ::  surprised, shock, oh
(O_O)  ::  stunned  ::  surprised, shock, stare
O_O  ::  shocked simple  ::  surprised, shock
(°ロ°)  ::  aghast  ::  shocked, surprised
(ﾟДﾟ;)  ::  horrified  ::  shock, scared
(☉_☉)  ::  staring  ::  surprised, shock
(⊙ω⊙)  ::  wow  ::  surprised, amazed
(*゜ロ゜)ノ  ::  whoa  ::  surprised, shocked
Σ(°△°|||)  ::  dismay  ::  shocked, horror
(ﾟοﾟ人))  ::  amazed  ::  surprised, wow
w(°ｏ°)w  ::  astonished  ::  surprised, wow
(ノ°ο°)ノ  ::  freaked out  ::  scared, shocked, panic
(((;ꏿ_ꏿ;)))  ::  trembling  ::  scared, fear, nervous
ヽ(°〇°)ﾉ  ::  panic  ::  shocked, scared, alarm
(⊙＿⊙')  ::  speechless  ::  shocked, stare
(ʘᗩʘ')  ::  jaw drop  ::  shocked, surprised
(○o○)  ::  surprised  ::  shock, oh
Σ(O_O)  ::  realization  ::  shock, oh no
(・□・;)  ::  uh oh  ::  oops, nervous, shock
:O  ::  open mouth  ::  surprised, gasp, shocked
(ﾟ∀ﾟ)  ::  excited stare  ::  surprised, wow
""",
        ),
        Source(
            "CONFUSED", "Confused", "・・?",
            """
(・・?)  ::  puzzled  ::  confused, question, what
(゜-゜)  ::  blank stare  ::  confused, meh, what
(￣ω￣;)  ::  perplexed  ::  confused, awkward
(⊙_☉)  ::  dazed  ::  confused, weird
ლ(ಠ_ಠლ)  ::  why  ::  confused, frustrated, what
(◎_◎;)  ::  dizzy  ::  confused, overwhelmed
(＠_＠)  ::  overwhelmed  ::  dizzy, confused
(°ー°〃)  ::  hmm  ::  confused, thinking
(・_・ヾ  ::  scratching chin  ::  thinking, confused
(￢_￢;)  ::  doubtful  ::  skeptical, confused
┐(￣ヘ￣;)┌  ::  no idea  ::  confused, shrug
(•ิ_•ิ)?  ::  questioning  ::  confused, what, question
(._.?)  ::  lost  ::  confused, question
(⊙.☉)7  ::  thinking hard  ::  confused, thinking
( ˇ෴ˇ )  ::  pondering  ::  thinking, hmm
(ㆆ_ㆆ)  ::  stare  ::  suspicious, judging
""",
        ),
        Source(
            "SHRUG", "Shrug", "ツ",
            """
¯\_(ツ)_/¯  ::  shrug  ::  whatever, dunno, idk, meh
¯\(°_o)/¯  ::  confused shrug  ::  dunno, idk, what
┐(´д｀)┌  ::  so what  ::  shrug, whatever
╮(￣ω￣;)╭  ::  oh well  ::  shrug, whatever
┐(︶▽︶)┌  ::  who knows  ::  shrug, dunno
ヽ(ー_ー )ノ  ::  meh  ::  shrug, whatever
╮(╯∀╰)╭  ::  cant be helped  ::  shrug, oh well
┐( ˘_˘ )┌  ::  unbothered  ::  shrug, whatever
╮(︶︿︶)╭  ::  resigned  ::  shrug, sigh
┐(￣∀￣)┌  ::  smug shrug  ::  whatever, shrug
¯\_( ͡° ͜ʖ ͡°)_/¯  ::  lenny shrug  ::  shrug, lenny, whatever
┐('～`;)┌  ::  helpless  ::  shrug, dunno
ʅ(°_°)ʃ  ::  no clue  ::  shrug, idk
""",
        ),
        Source(
            "GREETING", "Greeting", "o/",
            """
( ´ ▽ ` )ﾉ  ::  hello  ::  hi, wave, greeting
(^_^)/  ::  hi  ::  hello, wave
ヾ(＾∇＾)  ::  waving  ::  hello, hi, bye
(｡･∀･)ﾉﾞ  ::  hey there  ::  hi, hello, wave
o/  ::  wave  ::  hi, hello
\o  ::  wave left  ::  hi, hello, bye
(・ω・)ノ  ::  yo  ::  hi, hello, wave
(^-^*)/  ::  hiya  ::  hello, wave
(￣▽￣)ノ  ::  see you  ::  bye, wave, later
(*・ω・)ﾉ  ::  good morning  ::  hi, hello, morning
(・∀・)ノシ  ::  bye bye  ::  bye, goodbye, wave
(^_^)v  ::  peace  ::  victory, peace sign
(￣^￣)ゞ  ::  salute  ::  yes sir, respect, ok
(｀･ω･´)ゞ  ::  roger  ::  salute, ok, yes sir
(^_-)-☆  ::  wink star  ::  wink, cute, flirt
(^_-)  ::  wink  ::  flirt, joke
(^_~)  ::  playful wink  ::  wink, flirt
d(^_^)b  ::  thumbs up  ::  ok, good, approve, nice
(b ᵔ▽ᵔ)b  ::  double thumbs up  ::  ok, good, approve
(o^^)o  ::  high five  ::  yay, cheer
( ˙▿˙ )/\( ˙▿˙ )  ::  high five pair  ::  celebrate, friends
(^人^)  ::  please  ::  thanks, grateful, pray
(*´▽`*)ﾉ  ::  welcome  ::  hi, hello, greeting
(っ＾▿＾)っ  ::  coming over  ::  hi, hug, hello
(￣ー￣)ｂ  ::  approval  ::  ok, good, thumbs up, nice
""",
        ),
        Source(
            "SLEEPY", "Sleepy", "zZ",
            """
(－_－) zzZ  ::  sleeping  ::  sleep, tired, zzz
(∪｡∪)｡｡｡zzZ  ::  fast asleep  ::  sleep, zzz, night
(-.-)Zzz...  ::  dozing  ::  sleep, tired, nap
(´〜｀*) zzz  ::  drowsy  ::  sleepy, tired
(￣o￣) zzZZzzZZ  ::  snoring  ::  sleep, zzz
(=_=)  ::  exhausted  ::  tired, sleepy, done
(´-ω-`)  ::  sleepy  ::  tired, drowsy
(ᴗ˳ᴗ)  ::  peaceful sleep  ::  sleep, rest
(￣ρ￣)..zzZZ  ::  drooling asleep  ::  sleep, nap
(〜￣△￣)〜  ::  yawning  ::  sleepy, tired, yawn
(¦3[▓▓]  ::  tucked in bed  ::  sleep, bed, night
(－.－)…zzz  ::  nodding off  ::  sleepy, tired
(っ˘ω˘ς )  ::  cozy nap  ::  sleep, nap, comfy
(_ _)。゜zｚＺ  ::  out cold  ::  sleep, zzz
(*-ω-)ω-*)  ::  sleeping together  ::  sleep, couple, cuddle
( ￣ｰ￣)…  ::  bored  ::  tired, meh
(ᵕ.ᵕ)…  ::  calm  ::  relaxed, peaceful
(~_~)  ::  worn out  ::  tired, meh
_(:3 」∠)_  ::  flopped  ::  lazy, tired, collapse, done
""",
        ),
        Source(
            "ANIMALS", "Animals", "=^.^=",
            """
(=^･ω･^=)  ::  cat  ::  kitty, meow, neko
(=^‥^=)  ::  cat face  ::  kitty, meow
=^.^=  ::  kitty  ::  cat, meow
ฅ^•ﻌ•^ฅ  ::  cat paws  ::  kitty, meow, cute
(^・ω・^ )  ::  happy cat  ::  kitty, meow
(=①ω①=)  ::  wide eyed cat  ::  kitty, meow
(=^ ◡ ^=)  ::  smiling cat  ::  kitty, meow
＿φ(°-°=)  ::  cat writing  ::  kitty, note, write
ʕ•ᴥ•ʔ  ::  bear  ::  cute, teddy
ʕ •́؈•̀)  ::  grumpy bear  ::  bear, pout
ʕ ·(エ)· ʔ  ::  teddy bear  ::  bear, cute
(ᵔᴥᵔ)  ::  happy bear  ::  bear, cute
(・(ｪ)・)  ::  koala  ::  bear, cute
(／・ω・)／  ::  hamster wave  ::  hamster, hi
∪･ω･∪  ::  dog  ::  puppy, woof
U^ェ^U  ::  happy dog  ::  puppy, woof
▼・ᴥ・▼  ::  puppy  ::  dog, woof
(V●ᴥ●V)  ::  doggo  ::  dog, puppy
(\(•ᴥ•)/)  ::  bunny  ::  rabbit, cute
／(･ × ･)＼  ::  rabbit  ::  bunny, cute
(•ө•)  ::  bird  ::  chick, tweet
(・θ・)  ::  chick  ::  bird, tweet
(｀･Θ･´)  ::  angry bird  ::  bird, mad
('(00)')  ::  pig  ::  oink, piggy
(•(oo)•)  ::  piglet  ::  pig, oink
<°)))><  ::  fish  ::  sea, swim
><((((º>  ::  big fish  ::  sea, swim
>゜)))彡  ::  swimming fish  ::  fish, sea
(°)#))<<  ::  fish bones  ::  fish, skeleton
＜コ:彡  ::  squid  ::  sea, octopus
くコ:彡  ::  octopus  ::  squid, sea
~>°)~~~  ::  snake  ::  hiss, serpent
""",
        ),
        Source(
            "ACTIONS", "Actions", "┻━┻",
            """
(╯°□°)╯︵ ┻━┻  ::  table flip  ::  angry, rage, flip, flipping table
┬─┬ノ( º _ ºノ)  ::  put table back  ::  calm, table, unflip, restore
(ノಠ益ಠ)ノ彡┻━┻  ::  rage table flip  ::  table flip, angry, rage
┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻  ::  double table flip  ::  table flip, angry, rage
(┛◉Д◉)┛彡┻━┻  ::  shocked table flip  ::  table flip, angry
┬──┬◡ﾉ(° -°ﾉ)  ::  careful table  ::  table, calm, unflip
( ͡° ͜ʖ ͡°)  ::  lenny face  ::  lenny, smirk, suggestive, meme
( ͠° ͟ʖ ͡°)  ::  suspicious lenny  ::  lenny, skeptical
(☞ﾟヮﾟ)☞  ::  pointing  ::  you, point, this
☜(ﾟヮﾟ☜)  ::  pointing back  ::  me, point
(☞ﾟ∀ﾟ)☞  ::  finger guns  ::  you, point, cool
(•_•) ( •_•)>⌐■-■ (⌐■_■)  ::  deal with it  ::  sunglasses, cool, meme
(⌐■_■)  ::  cool shades  ::  sunglasses, cool
ᕦ(ò_óˇ)ᕤ  ::  flexing  ::  strong, muscle, gym, power
ᕙ(⇀‸↼‶)ᕗ  ::  strong  ::  flex, muscle, power
ε=ε=ε=┌(;*´Д`)ﾉ  ::  running away  ::  run, flee, escape, panic
ε=┌( ≧▽≦)┘  ::  dashing  ::  run, hurry, excited
┌( ಠ_ಠ)┘  ::  march  ::  walk, determined
♪┏(・o･)┛♪  ::  dancing  ::  dance, party, music
┏(＾0＾)┛  ::  disco  ::  dance, party
♪ ヽ(^^ヽ)  ::  groove  ::  dance, music
(~‾▿‾)~  ::  wiggle  ::  dance, happy
〜(￣▽￣〜)  ::  shimmy  ::  dance, happy
(ﾉ^_^)ﾉ  ::  jazz hands  ::  dance, yay
( ・_・)ノ⌒●  ::  throw ball  ::  throw, play, ball
༼ つ ◕_◕ ༽つ  ::  give  ::  gimme, hug, want
(　-_･) ︻デ═一  ::  sniper  ::  aim, shoot, target
(ﾉ｀Д)ﾉ  ::  throwing  ::  angry, throw
(ｏ・_・)ノ”(ᴗ_ ᴗ。)  ::  head pat  ::  pat, comfort, there there
✍(◔◡◔)  ::  writing  ::  write, note, study
φ(．．)  ::  taking notes  ::  write, note, study
(っ•́｡•́)♪♬  ::  singing  ::  sing, music, song
♪～(´ε｀ )  ::  whistling  ::  whistle, innocent, music
(ʃƪ˘ﻬ˘)  ::  savoring  ::  tasty, yum, enjoy
(*´ڡ`●)  ::  yummy  ::  food, tasty, eat
(っ˘ڡ˘ς)  ::  eating  ::  food, yum, eat
(￣～￣)  ::  chewing  ::  eat, food
( ´ ∀ `)ノ～ ♡  ::  blowing hearts  ::  love, send
(ﾉ´з｀)ノ  ::  come here  ::  want, kiss
(╯✧▽✧)╯  ::  excited grab  ::  want, excited
(ﾉ*>∀<)ﾉ♡  ::  love throw  ::  love, excited
( •_•)σ  ::  poke  ::  hey, point
(*￣▽￣)b  ::  nice one  ::  good, thumbs up, ok
(ノ・ェ・)ノ  ::  cheer on  ::  go, support, yay
ヽ(ﾟ∀ﾟ)ﾉ  ::  party  ::  celebrate, yay, dance
""",
        ),
    )
}

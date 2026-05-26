package xyz.langyo.minecraft.mcp.common;

public class McpOverlayLogic {

    private static final int MARGIN = 10;
    private static final int BTN_SIZE = 32;
    private static final int ICON_SIZE = 32;

    private static final String RESUME_ICON_DATA =
            "00000000ff181615ff1b1918ff1b181800000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000ff1b1918ff1c1919ff1a191800000000" +
            "ff1a1817ffa69a96ffa69a95ffa59994ff69625fff4a4442ff595250ff5e5653" +
            "ff635b59ff524c4aff4f4a47ff4c4644ff45403dff4a4442ff554e4cff514b48" +
            "ff494341ff4d4744ff443e3cff3c3835ff443f3cff514947ff4b4443ff4e4846" +
            "ff59514fff514b49ff46413fff675f5dffa69994ffa79b96ff9b8f8bff161413" +
            "ff1c1a19ffa19590ff5f5856ff635c59ff363330ff171613ff211f1bff25221e" +
            "ff24211dff1b1a18ff1c1a18ff1e1c19ff1e1c19ff1d1b18ff1c1b19ff1e1c19" +
            "ff201e1aff22201cff1d1c19ff1a1917ff211d19ff24211cff23201cff22201c" +
            "ff231f1cff221e1bff181715ff312e2cff605956ff5c5452ff8f827eff171514" +
            "ff1d1b1affa69a95ff5e5755ff20201fff261b11ff39200dff3b220fff412510" +
            "ff3d2310ff341e0eff341f0fff3c2311ff3f2410ff3c220fff382110ff3a1f0b" +
            "ff3e3127ff2c2d2d00000000ff22170dff2f1e0aff2f1c0cff2f1b0dff2b190d" +
            "ff271a0bff301e0cff391f0dff23180fff1e1e1dff5c5452ff9b8e89ff181616" +
            "00000000ff655e5cff2e2b2aff2f1d0fff40240fff532d12ff4b2911ff492810" +
            "ff542d12ff4e2a11ff452610ff4f2b11ff5c3315ff563014ff4b260cff493628" +
            "ff373636ff161717ff141009ff361f0dff70281dffc1322effc43531ffc5312d" +
            "ff912922ff421f10ff38220bff3f220eff29190eff2f2c2bff5c565400000000" +
            "00000000ff504a48ff181715ff3d220eff532f15ff5a3416ff512d13ff492811" +
            "ff522d12ff4f2b11ff563014ff4c2911ff542f13ff542f14ff44230bff3e3128" +
            "ff26292cff18120aff552115ffb1312cffc42f2dff7a1916ff6c1613ff6a1613" +
            "ffb42625ffd73936ff80271fff3a1e0bff39200dff161514ff48434200000000" +
            "00000000ff524b4aff1d1b19ff3b210fff583216ff5b3316ff522e14ff4c2b13" +
            "ff4c2a11ff552f13ff542e13ff573014ff552f13ff512c11ff311e10ff292624" +
            "ff1e1f20ff18100affb7332effe12f2dffa22421ff211207ff271908ff2f1c0b" +
            "ff311409ff8e1d1affdd3130ff5a2016ff2f1d0bff241f1cff56504e00000000" +
            "00000000ff4c4645ff1f1d1aff3c2310ff512d14ff502d14ff522f14ff573216" +
            "ff4f2c13ff563115ff593115ff5d3314ff2d1b0fff1b1008ff171411ff242424" +
            "ff101919ff501e1cffd4322fff6e1410ffc12a27ffac2c27ff3b200dff46260f" +
            "ff40250fff261307ffae2422ffab2c28ff29190bff241f1bff544e4c00000000" +
            "00000000ff58514fff23211eff432510ff492811ff4e2c13ff5a3316ff603717" +
            "ff553116ff512e14ff5d3416ff29190dff797674ff898585ff999391ff605b5a" +
            "ff444c4bff883734ffb4221fff261c19ff410d0affd12c2affa52c28ff2f1b0d" +
            "ff42230dff38210dff621812ffc02826ff33140cff1c1d1aff50494800000000" +
            "00000000ff5b5452ff22201dff412410ff4e2c13ff583216ff5c3315ff502d13" +
            "ff4d2a12ff542d10ff24160bff7b7877ffd4cac6ffdacec9ffdacec9ff6c6665" +
            "ff565a59ff933835ffac1f1cff726865ff60615fff410804ffd22d2bffa12b27" +
            "ff361d0cff34220cff5f1c15ffbc2423ff34120cff171a18ff3d383700000000" +
            "00000000ff534d4bff211f1cff3d2310ff593216ff5f3618ff512e14ff482811" +
            "ff4c2a11ff37271bff82807effd6cbc7ffc4b8b4ffcbbfbaff908885ff3f3d3d" +
            "ff333d3cff631412ffb62320ff796e6affc1b7b3ff474845ff4c120effd02a28" +
            "ffa62b26ff261a0aff6e211affc42827ff2e120aff191b18ff413c3a00000000" +
            "00000000ff4b4644ff1e1d1aff3b220fff492912ff512f15ff4e2c13ff4f2c12" +
            "ff512d12ff282626ffd2cac7ffcec2beffc2b7b2ffd4c8c3ff99918eff3d3c3b" +
            "ff3a3f3fff431c18ffc62523ff953632ff9d9894ffa79f9d00000000ff571610" +
            "ffc52927ff9a2824ffb72a26ff8c1d1aff211409ff211e1cff554f4d00000000" +
            "00000000ff4d4846ff201e1bff38200fff4e2b12ff552f14ff4f2c13ff522d13" +
            "ff4f2b11ff2c2926ffbfb7b5ffcbc0bdffc9beb9ffd1c6c1ff958d8bff1f1f1f" +
            "ff2e2d2dff565350ff741915ffd12826ff9d413dff666765ff11141100000000" +
            "ff671914ffde302dffb72423ff3c140bff2e1d0cff24201dff4c484700000000" +
            "00000000ff484341ff201e1bff3b2210ff4f2b12ff5e3516ff593215ff502c12" +
            "ff472710ff2d2b28ffcfc9c6ffd7ccc8ffc7bcb8ffc8bcb8ffc7bcb8ff434140" +
            "ff4c4947ffccc2beff766762ff6a100dffbb211effa12421ff932824ff9c2e2a" +
            "ffbc2927ffae2321ff581610ff3b1f0cff42240fff211f1dff544e4d00000000" +
            "00000000ff403c3aff201e1bff402410ff4e2b13ff563116ff543015ff4d2b13" +
            "ff41240eff292725ff9a9794ffa49e9bff99938fff928c88ffa29b96ff75716e" +
            "ff686461ff968e8bff87827fff46423fff47211eff871e1cffa2211fff99201d" +
            "ff6d1a14ff39150bff311d0bff4c2910ff472711ff22201dff524d4c00000000" +
            "00000000ff3e3a38ff201e1bff412510ff542e13ff522e14ff4d2c14ff3e2410" +
            "ff2a1809ff22201fffb8b4b2ff9d9997ff807c7cff96918dff999292ff878180" +
            "ff7e7875ff6e6765ff706867ff89837fff6a6966ff2f312e00000000ff190e06" +
            "ff190f05ff40230eff4c2911ff4b2911ff492912ff22201eff48444300000000" +
            "00000000ff443f3dff201e1bff3c2310ff553014ff5d3719ff482411ff464f1f" +
            "ff83a941ff3a451fff706a71ff4f5c31ff86a940ff404a25ff464e2dff7d9e3c" +
            "ff8cac45ff89aa46ff8daf46ff273112ff5c6e2eff96b54dff89aa47ff8aac45" +
            "ff566929ff3c220eff552f14ff512d13ff4f2d15ff22201eff49454400000000" +
            "00000000ff504a48ff211f1cff3e2410ff482812ff593518ff462210ff4d5b22" +
            "ffa4d34fff82a63fff2e3814ff8bb241ff9fce4dff394519ff5c7928ff8db543" +
            "ff334512ff415818ff7ba137ff172205ff6c8835ff8fb546ff222d0eff698330" +
            "ff8ebb46ff2e240cff4f2a13ff4c2b14ff412713ff21201eff4a464500000000" +
            "00000000ff413d3bff211f1dff432611ff502d14ff5f3819ff482311ff4b5922" +
            "ff8db744ff7da33bff8db845ff7da33cff8cb843ff374319ff5e7a2cff6d9131" +
            "ff403a40ff484441ff24271cff211f1dff6c8935ff81a53eff1b210eff587229" +
            "ff87b541ff2c220cff502b13ff543014ff3e2512ff22201fff55504e00000000" +
            "00000000ff413c3bff201e1cff402511ff5a3216ff5e3719ff452110ff45551d" +
            "ff81ac3eff303e14ff62862cff293810ff8ab640ff2c3b13ff577528ff628828" +
            "ff897e82ffb8acacff8b8183ff6f6668ff5e7c2bff88b540ff81a93cff84ad3d" +
            "ff577829ff251808ff512d14ff4c2b13ff3c2412ff22201eff59535100000000" +
            "00000000ff3f3b39ff1e1c1aff3e2410ff573116ff563317ff401f0fff3b4b18" +
            "ff689730ff11170600000000ff202712ff72a234ff273511ff506f25ff5b8528" +
            "ff484d38ff4c5931ff5a7d2aff262d17ff506f26ff598129ff131905ff1a2308" +
            "00000000ff2f190bff542f15ff5a3216ff472a14ff201e1dff4b474600000000" +
            "00000000ff3b3736ff1c1b19ff3e230fff4f2d13ff563418ff452211ff394816" +
            "ff5e8b28ff1a1f11ff7f777cff505540ff609026ff24320fff24360bff67942f" +
            "ff649129ff67922bff6b9c30ff232f12ff486621ff4f752300000000ff2d180b" +
            "ff3a210eff492912ff4e2d14ff543015ff492b15ff1e1d1cff423e3e00000000" +
            "00000000ff4a4544ff201e1bff3e230fff512d14ff5a3519ff512d14ff1b1b08" +
            "ff182506ff161711ffb3adacff58564eff101d00ff272b1bff2e2d26ff152004" +
            "ff1c2608ff1a2407ff172305ff171a0eff1a220aff152005ff16130eff452812" +
            "ff563015ff552f14ff4c2b14ff462813ff462914ff1d1c1bff3d393800000000" +
            "00000000ff4b4544ff211f1cff3a210fff5a3317ff5b3519ff512e14ff3f220e" +
            "ff271308ff181817ff858280ff9f9793ff7d7474ff8c8380ffaaa09cff7d7575" +
            "ff746c6bff726b6aff756d6dff877f7cff736d6bff3d3b3cff151310ff3f2410" +
            "ff583216ff593216ff4d2b13ff4f2c13ff502f15ff1d1c1bff3f3b3b00000000" +
            "00000000ff3e3938ff1e1c19ff331e0eff543015ff553117ff4d2c14ff573115" +
            "ff542f13ff1e1812ff4d4d4dff918e8cffb2aaa6ffb6aca8ffb2a7a3ffbaafaa" +
            "ffb7aba7ffb6aba6ffb5aba6ffa8a09dff837e7cff3a3937ff1a130eff432611" +
            "ff4b2a13ff4d2b13ff4f2d14ff532f14ff4f2d14ff1f1d1aff3a363600000000" +
            "00000000ff383432ff201e1bff341f0eff462812ff4d2c14ff4c2b14ff4b2a13" +
            "ff502d14ff44250fff19120dff50504fff807e7cff9f9794ff9f9693ff9d9591" +
            "ffa09793ff9e9592ff968f8cff757170ff41403fff17100aff361e0dff4a2912" +
            "ff502c13ff462812ff492a13ff553015ff482a13ff1e1c1aff413c3a00000000" +
            "00000000ff312d2cff191816ff311c0cff472710ff512d14ff4e2c14ff4c2c14" +
            "ff553117ff532f15ff3f2410ff18110bff434242ff666362ff6a6664ff676462" +
            "ff686563ff6b6866ff64615fff3b3a39ff140e09ff38200eff4d2c14ff4d2c14" +
            "ff502d14ff4b2b13ff4e2d14ff5a3316ff422510ff171513ff322f2d00000000" +
            "00000000ff5b5350ff3b3734ff261b13ff3a210fff4b2a12ff4f2d14ff553016" +
            "ff543116ff482a14ff4e2d14ff3f2410ff1e140cff1c1b1aff222221ff222221" +
            "ff212120ff222222ff1d1c1bff1b1109ff3b210dff512d12ff583115ff553016" +
            "ff532f15ff543015ff583216ff4f2d14ff312114ff373330ff5b545100000000" +
            "ff191716ff948783ff595250ff272726ff251c14ff2f1d0fff3a2515ff422916" +
            "ff3b2515ff332012ff3d2615ff332113ff321f10ff1c150eff15120eff16120e" +
            "ff16120eff15120eff1a140eff29190dff372212ff442915ff402815ff432a17" +
            "ff3e2615ff402715ff402613ff332215ff242423ff524c4aff897d7aff161413" +
            "ff1a1717ff90847fff4e4846ff554e4bff33302eff1b1b1aff222120ff242423" +
            "ff222220ff1b1b1aff1a1b1aff1e1e1dff20201eff211f1dff22201dff201e1c" +
            "ff1f1d1bff201e1bff21201dff201f1eff1e1e1dff1f1f1eff232320ff1d1d1c" +
            "ff1f1f1eff1b1c1b00000000ff292726ff4b4441ff484240ff786d6a00000000" +
            "ff181716ff8a7e7aff877a76ff968984ff5d5552ff393432ff46413fff494442" +
            "ff403b39ff393432ff363230ff46413fff474140ff474240ff433f3eff393534" +
            "ff363230ff353230ff413c3aff46413fff413c3aff393533ff3b3735ff413b39" +
            "ff443e3bff3c3735ff312d2bff504846ff857873ff786c68ff6e646100000000" +
            "000000000000000000000000ff15131300000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000"
            ;

    private static final String TRANSFER_ICON_DATA =
            "00000000ff171514ff1a1817ff19171600000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000ff1a1817ff1a1918ff19171600000000" +
            "ff181616ffa29691ffa29692ff9f938fff696260ff4b4645ff5b5452ff605956" +
            "ff655d5bff544e4dff504b49ff4d4846ff474241ff4c4745ff56504eff524c4a" +
            "ff4a4543ff4d4745ff45403eff403b39ff484240ff514b48ff4b4644ff4e4847" +
            "ff5a5351ff534d4bff484342ff67605effa39792ffa49893ff988d88ff161413" +
            "ff1a1817ff9c908cff605957ff615a58ff363331ff181715ff22201eff272421" +
            "ff25221fff1d1c1aff1d1c19ff1e1c1aff1f1d1aff1e1c1aff1d1c1aff1f1d1b" +
            "ff201e1bff22201dff1e1c1aff1b1a18ff201d1bff25221eff25221fff24211f" +
            "ff23211eff23201eff1c1a18ff322f2dff615a58ff5d5654ff8f827eff171514" +
            "ff1b1918ffa09490ff5c5654ff1f1f1fff251b12ff36200fff392312ff402713" +
            "ff3d2513ff321f11ff332011ff3b2513ff3e2613ff3b2412ff372313ff38200e" +
            "ff3d3128ff2c2d2d00000000ff21170eff39210fff382110ff382211ff321f10" +
            "ff342010ff372111ff372110ff241911ff201f1fff5c5553ff988b87ff181615" +
            "00000000ff635c5aff2e2b29ff2d1d11ff3d2411ff502e15ff492a13ff482a13" +
            "ff522f15ff4c2c13ff442712ff4d2c14ff5a3518ff553217ff49280fff48372a" +
            "ff363635ff141617ff1c130cff482912ff593417ff4f2d13ff482912ff482912" +
            "ff4b2b14ff4d2c14ff533016ff452914ff2a1b10ff302d2bff5b545200000000" +
            "00000000ff4e4847ff191716ff3a2210ff503017ff583419ff4f2e15ff482a13" +
            "ff512e15ff4d2c14ff543116ff4b2b13ff533016ff533016ff41230cff3d332a" +
            "ff262a2cff20160dff4b2c14ff573317ff553115ff4f2d14ff452812ff492a13" +
            "ff4f2e15ff4e2d15ff502f16ff4f2e15ff3a2311ff181615ff46414000000000" +
            "00000000ff514b49ff1e1c1aff382211ff563318ff583418ff502f16ff4b2c15" +
            "ff4b2b14ff533016ff533015ff543116ff543015ff502d13ff2f1e11ff292724" +
            "ff1d2021ff2a1b0fff5a3316ff522f15ff452812ff452811ff452812ff4f2e15" +
            "ff593418ff502f17ff4a2b15ff4f2e16ff442914ff201e1cff534c4b00000000" +
            "00000000ff4b4645ff201e1cff3b2412ff4f2f16ff4e2e16ff523017ff563419" +
            "ff4e2f16ff553217ff563216ff5a3316ff2d1c0fff1b1109ff181512ff232424" +
            "ff1f2020ff16120fff1d130aff291a0eff492a13ff4d2c14ff4f2e14ff502f15" +
            "ff523017ff512f16ff4d2d16ff513017ff4d2e16ff201e1cff504b4a00000000" +
            "00000000ff554f4eff23201eff412613ff462913ff4b2c15ff593419ff5e381a" +
            "ff553318ff512f16ff5c3518ff291a0eff7a7674ff888584ff999390ff5c5957" +
            "ff5b5957ff97928fff807c7aff605b59ff26160aff5a3418ff5a3418ff492b13" +
            "ff452812ff4d2c14ff4c2d15ff4c2e16ff4f2f17ff1f1d1bff4c474600000000" +
            "00000000ff585250ff23201dff3f2612ff4c2c15ff563318ff593519ff4f2e15" +
            "ff4c2c14ff522e13ff25170dff7b7876ffd3cac5ffd9cdc7ffd8ccc7ff686362" +
            "ff6c6866ffdbd0cbffd0c3beffc2b7b3ff676361ff25170cff543015ff4f2d14" +
            "ff512f15ff4e2d15ff452914ff4a2c15ff492c16ff1d1b1aff3b373600000000" +
            "00000000ff504a49ff221f1cff3b2411ff573418ff5c371bff4f2f16ff462913" +
            "ff4a2b13ff37281cff83807effd3c9c4ffc1b5b1ffc7bcb7ff8e8683ff3b3b3b" +
            "ff3e3e3dff9e9591ffc8bdb9ffc3b7b3ffb7aca8ff565352ff2d1f13ff492a13" +
            "ff583418ff54331aff51321aff573419ff432915ff1e1c1aff3f3b3a00000000" +
            "00000000ff464241ff1f1d1bff382211ff472a14ff4f2f17ff4d2d16ff4e2e16" +
            "ff4f2e14ff272625ffd0c8c4ffcabebaffbfb3afffd1c5c1ff968f8bff393939" +
            "ff3b3b3affa29995ffc9bdb9ffbbb0acffc0b4afff9c9591ff191715ff3a2310" +
            "ff4d2f18ff4d2f19ff4f3019ff513018ff402714ff211f1dff524c4b00000000" +
            "00000000ff4a4544ff201e1cff372111ff4b2c15ff523016ff4f2e16ff512f16" +
            "ff4e2d13ff2c2926ffbcb4b0ffc6bbb7ffc5bab6ffcdc1bdff918a86ff212121" +
            "ff252524ff9b928effc3b7b3ffbaafaaffc0b5b0ff908986ff1c1915ff3a2311" +
            "ff4f3019ff55341aff553319ff4d2e16ff412714ff22201eff4a454500000000" +
            "00000000ff474241ff201e1cff392312ff4d2d15ff5b3619ff583418ff502e15" +
            "ff462812ff2d2b28ffcbc5c1ffd2c7c2ffc3b8b3ffc2b7b2ffc1b7b2ff43403f" +
            "ff4f4c4affccc2bdffccc2bdffbeb2aeffc2b7b2ff8c8784ff1d1a16ff3e2513" +
            "ff553318ff543219ff523017ff553117ff472a15ff221f1dff514b4a00000000" +
            "00000000ff403b3aff211e1cff3e2513ff4c2c15ff553319ff533218ff4c2d16" +
            "ff402511ff2a2825ff999693ffa29d99ff98928fff908a87ff9f9994ff74706d" +
            "ff6b6764ff968f8bff8d8783ff8c8481ff8a8380ff63605fff1d1a18ff402613" +
            "ff573317ff4c2d15ff482a14ff502f16ff472b15ff211f1dff504b4a00000000" +
            "00000000ff3e3a38ff201e1bff3e2513ff512f16ff513018ff4d2f18ff3d2613" +
            "ff2a190bff242221ffb6b3b0ff9b9794ff7f7b7aff948f8aff968f8eff847f7c" +
            "ff7c7673ff6e6865ff716a67ff7f7873ff78736eff575451ff171411ff2d1c0d" +
            "ff3c210fff4b2a13ff492b14ff4a2c15ff482c16ff22201dff47424100000000" +
            "00000000ff443f3eff211e1cff3a2312ff523017ff5a381bff472614ff495022" +
            "ff87aa45ff3c4722ff716b70ff525d33ff8aaa46ff434c27ff474f2eff7f9f40" +
            "ff8ead49ff8caa49ff8fb04aff2b3416ff5d7030ff95b34dff8ca94aff8bac49" +
            "ff53672bff3c2410ff543117ff503017ff4e3019ff221f1dff48434300000000" +
            "00000000ff504a49ff22201dff3c2513ff442914ff56361bff462413ff4e5d25" +
            "ffa8d456ff86a844ff303915ff8eb246ffa3cf53ff3a461bff607b2dff91b649" +
            "ff364715ff445a1bff7ea13bff1a2408ff6f8a38ff8fb348ff24300fff6c8635" +
            "ff91bb4bff2f240fff4d2c16ff492e17ff402918ff211f1dff49454400000000" +
            "00000000ff423e3cff221f1dff412814ff4d2e16ff5b391cff472514ff4d5b25" +
            "ff91b94aff81a440ff90b74aff81a441ff8fb748ff38451bff627c31ff709036" +
            "ff413a3fff494542ff272a1eff23211eff6d8837ff81a242ff1b200fff5b732d" +
            "ff89b346ff2c230fff4f2d17ff523219ff3e2817ff221f1eff534d4c00000000" +
            "00000000ff403c3aff221f1cff3e2613ff563319ff5a381cff432313ff475621" +
            "ff83aa42ff324017ff668731ff2c3a13ff8cb546ff2f3d16ff59742eff65872d" +
            "ff877c7effb3a6a5ff877d7eff6d6465ff5f7a2eff88b044ff83a741ff87ad42" +
            "ff59772cff261a0bff502f17ff4b2e17ff3c2717ff221f1dff554f4e00000000" +
            "00000000ff3e3a39ff201d1bff3d2513ff533218ff53331aff3e2012ff3d4c1c" +
            "ff6a9436ff121707ff101505ff222814ff739d3aff283514ff526d2aff5d812d" +
            "ff494c39ff4c5833ff5c7b2eff282e18ff516c2aff597b2cff151c06ff1c260a" +
            "00000000ff2f1b0dff533118ff58351aff472d19ff201e1dff49454400000000" +
            "00000000ff3c3837ff1e1c1aff3c2513ff4c2e16ff53341aff432313ff3c4a1b" +
            "ff61892fff1b1f12ff81797dff545843ff618a2bff253211ff27380eff6a9035" +
            "ff668c2fff678e31ff6c9635ff242f13ff4a6325ff506f2700000000ff2d1a0d" +
            "ff3b2311ff492b15ff4d2f18ff53331aff482e19ff1e1c1bff413d3c00000000" +
            "00000000ff4b4645ff211f1cff3c2412ff4f2f17ff58371dff4f2f18ff1d1d0a" +
            "ff1b2809ff181912ffb4acabff5b594fff131f02ff292c1dff302f28ff192307" +
            "ff1f290cff1c260aff192407ff181b10ff1d240dff182308ff17140fff442a15" +
            "ff553319ff543218ff4a2d17ff442b18ff462d1aff1e1c1bff3c383600000000" +
            "00000000ff4a4544ff22201dff392312ff57351aff58371cff503018ff3e2411" +
            "ff28150aff191918ff827f7dff9d9692ff7b7374ff88807dffa59b97ff7a7171" +
            "ff726a6aff726a69ff726b6aff827a76ff716b68ff3e3c3cff161311ff3e2613" +
            "ff563419ff573419ff4b2d16ff4d2f18ff50321bff1e1c1aff3e3a3900000000" +
            "00000000ff3d3937ff1f1c1aff301e10ff513118ff52321aff4b2d17ff563318" +
            "ff533217ff1e1913ff4c4c4bff8d8a88ffaca4a0ffb0a5a1ffaca09cffb2a7a1" +
            "ffaea39effaea29effaba19cff9f9693ff7c7774ff383735ff1a140fff412713" +
            "ff4a2c16ff4b2d16ff4e2f17ff513118ff4e3019ff1f1d1aff38343300000000" +
            "00000000ff373331ff211e1cff321f11ff432915ff4a2d17ff4a2d17ff492c16" +
            "ff502f17ff432711ff19130eff4e4e4dff7b7876ff98918dff99908cff988f8c" +
            "ff99908dff958d89ff8e8683ff6d6967ff3e3c3bff16100aff341f0fff482a14" +
            "ff4d2e16ff452a15ff482c17ff533219ff462c17ff1f1d1aff3e3a3800000000" +
            "00000000ff302d2cff1b1916ff301e0fff452913ff4e2f17ff4c2e17ff4a2e18" +
            "ff54341aff513118ff3d2512ff18120cff3f3f3eff615e5dff656260ff64615f" +
            "ff65615fff666260ff5d5a58ff373635ff150f0aff36200fff4c2d16ff4b2d16" +
            "ff4e2f17ff4a2c16ff4d2f18ff58351aff422813ff181613ff312d2c00000000" +
            "00000000ff595250ff3c3835ff261c14ff382211ff472b15ff4c2e18ff523219" +
            "ff523219ff472c17ff4e2f18ff412713ff20160eff1e1c1bff222221ff222120" +
            "ff212120ff222221ff1c1b1aff1c130bff3b220fff502f15ff573318ff543319" +
            "ff523118ff533218ff57351aff4e3018ff322316ff383331ff5b545100000000" +
            "ff181615ff908480ff5c5654ff292929ff251d16ff2d1e12ff392617ff422b19" +
            "ff3b2717ff332215ff3e2818ff362416ff352213ff1e1710ff16130fff17130f" +
            "ff17130fff16130fff1b150eff2a1b0fff382414ff452b18ff412a18ff432b19" +
            "ff3e2817ff3f2918ff402816ff342417ff282726ff554f4dff897d79ff151313" +
            "ff181615ff8b807cff514b49ff57504eff34312fff1c1b1bff232221ff272523" +
            "ff242321ff1d1c1bff1c1b1bff201f1eff21201fff22201eff23201eff211e1c" +
            "ff201d1bff201e1cff221f1dff211f1eff1f1f1eff20201eff252321ff1f1f1e" +
            "ff212020ff1d1d1dff151515ff2b2928ff4d4745ff4b4543ff766b6800000000" +
            "ff171515ff857975ff817571ff8f827eff5a5351ff393433ff45403fff484341" +
            "ff3f3b39ff393433ff363230ff46403fff46413fff464140ff423e3dff383433" +
            "ff363231ff363230ff3f3b39ff453f3eff403b39ff393432ff3c3735ff423c3a" +
            "ff443f3dff3d3837ff332f2eff4e4745ff7f736fff766b67ff6d635f00000000" +
            "000000000000000000000000ff14121200000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000"
            ;

    private static int[] decodeIcon(String hex) {
        int[] pixels = new int[ICON_SIZE * ICON_SIZE];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (int) Long.parseLong(hex.substring(i * 8, i * 8 + 8), 16);
        }
        return pixels;
    }

    private static int[] resumePixels;
    private static int[] transferPixels;

    private static int[] getResumePixels() {
        if (resumePixels == null) resumePixels = decodeIcon(RESUME_ICON_DATA);
        return resumePixels;
    }

    private static int[] getTransferPixels() {
        if (transferPixels == null) transferPixels = decodeIcon(TRANSFER_ICON_DATA);
        return transferPixels;
    }

    public static class ButtonBounds {
        public int x, y, w, h;
        public ButtonBounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public boolean hit(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static ButtonBounds calcIconBounds(int screenW) {
        return new ButtonBounds(screenW - BTN_SIZE - MARGIN, MARGIN, BTN_SIZE, BTN_SIZE);
    }

    private static void drawPixelIcon(McpRenderer r, int bx, int by, int[] pixels) {
        for (int py = 0; py < ICON_SIZE; py++) {
            for (int px = 0; px < ICON_SIZE; px++) {
                int color = pixels[py * ICON_SIZE + px];
                if (color != 0) {
                    r.fill(bx + px, by + py, bx + px + 1, by + py + 1, color);
                }
            }
        }
    }

    public static void renderResumeButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        drawPixelIcon(r, b.x, b.y, getResumePixels());
        ReflectionHelper.setOverlayButtonBounds(b.x, b.y, b.w, b.h, 0, 0, 0, 0);
    }

    public static void renderTransferButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        drawPixelIcon(r, b.x, b.y, getTransferPixels());
        ReflectionHelper.setTransferButtonBounds(b.x, b.y, b.w, b.h);
    }
}

"""Builds docs/data/catalog.json (world perfume catalog) and docs/data/boyner.json.

World data: Fragrantica-derived CSV (perfumes with notes, accords, longevity, sillage).
Boyner data: brand list + most-favourited perfumes from Boyner's public perfume category page.
If Boyner cannot be reached (e.g. bot protection), the previous boyner.json is kept.

Usage: python scripts/build_catalog.py [--src path/to/perfumes_actual.csv]
"""
import csv, json, os, re, sys, urllib.request, io, datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'docs', 'data')
SRC_URL = 'https://raw.githubusercontent.com/aStyxxx/dataset_Fragrantica_perfumes/HEAD/perfumes_actual.csv'
BOYNER_URL = 'https://www.boyner.com.tr/parfum-x-c4001'
UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36'

ACCORD_TR = {
    'woody': 'Odunsu', 'sweet': 'Tatlı', 'powdery': 'Pudralı', 'citrus': 'Narenciye', 'aromatic': 'Aromatik',
    'fruity': 'Meyveli', 'fresh spicy': 'Taze baharatlı', 'warm spicy': 'Sıcak baharatlı', 'floral': 'Çiçeksi',
    'amber': 'Amber', 'musky': 'Misk', 'vanilla': 'Vanilya', 'white floral': 'Beyaz çiçek', 'fresh': 'Fresh',
    'green': 'Yeşil', 'rose': 'Gül', 'balsamic': 'Balsamik', 'earthy': 'Topraksı', 'animalic': 'Hayvansı',
    'patchouli': 'Paçuli', 'soft spicy': 'Yumuşak baharatlı', 'leather': 'Deri', 'aquatic': 'Su', 'iris': 'İris',
    'lactonic': 'Sütlü', 'tropical': 'Tropik', 'herbal': 'Bitkisel', 'smoky': 'İsli', 'lavender': 'Lavanta',
    'oud': 'Ud', 'violet': 'Menekşe', 'yellow floral': 'Sarı çiçek', 'ozonic': 'Ozonik', 'mossy': 'Yosunsu',
    'nutty': 'Fındıksı', 'caramel': 'Karamel', 'marine': 'Deniz', 'cinnamon': 'Tarçın', 'tobacco': 'Tütün',
    'tuberose': 'Sümbülteber', 'almond': 'Badem', 'coconut': 'Hindistan cevizi', 'honey': 'Bal',
    'aldehydic': 'Aldehitli', 'salty': 'Tuzlu', 'cacao': 'Kakao', 'coffee': 'Kahve', 'metallic': 'Metalik',
    'cherry': 'Kiraz', 'rum': 'Rom', 'chocolate': 'Çikolata', 'conifer': 'Çam', 'soapy': 'Sabunsu', 'sour': 'Ekşi',
    'anis': 'Anason', 'mineral': 'Mineral', 'savory': 'Tuzlu', 'bitter': 'Acı', 'whiskey': 'Viski', 'camphor': 'Kafur',
    'beeswax': 'Balmumu', 'wine': 'Şarap', 'vinyl': 'Vinil', 'plastic': 'Plastik', 'sand': 'Kum', 'terpenic': 'Terpenik',
    'industrial glue': 'Yapışkan', 'champagne': 'Şampanya', 'gourmand': 'Gurme', 'smoke': 'Duman',
}

NOTE_TR = {
    'musk': 'Misk', 'bergamot': 'Bergamot', 'vanilla': 'Vanilya', 'sandalwood': 'Sandal ağacı', 'amber': 'Amber',
    'patchouli': 'Paçuli', 'jasmine': 'Yasemin', 'rose': 'Gül', 'cedar': 'Sedir', 'vetiver': 'Vetiver',
    'tonka bean': 'Tonka fasulyesi', 'mandarin orange': 'Mandalina', 'pink pepper': 'Pembe biber',
    'orange blossom': 'Portakal çiçeği', 'lemon': 'Limon', 'cardamom': 'Kakule', 'lavender': 'Lavanta',
    'leather': 'Deri', 'iris': 'İris', 'violet': 'Menekşe', 'grapefruit': 'Greyfurt', 'saffron': 'Safran',
    'white musk': 'Beyaz misk', 'benzoin': 'Benzoin', 'ginger': 'Zencefil', 'labdanum': 'Labdanum',
    'incense': 'Tütsü', 'lily-of-the-valley': 'Müge', 'lily of the valley': 'Müge', 'cinnamon': 'Tarçın',
    'geranium': 'Sardunya', 'ylang-ylang': 'Ylang-ylang', 'ylang ylang': 'Ylang-ylang', 'oakmoss': 'Meşe yosunu',
    'oak moss': 'Meşe yosunu', 'pear': 'Armut', 'agarwood (oud)': 'Ud', 'agarwood': 'Ud', 'oud': 'Ud',
    'peach': 'Şeftali', 'freesia': 'Frezya', 'peony': 'Şakayık', 'neroli': 'Neroli', 'orange': 'Portakal',
    'woody notes': 'Odunsu notalar', 'woodsy notes': 'Odunsu notalar', 'nutmeg': 'Muskat', 'raspberry': 'Ahududu',
    'black currant': 'Frenk üzümü', 'blackcurrant': 'Frenk üzümü', 'cassis': 'Frenk üzümü', 'ambergris': 'Amber balığı',
    'tuberose': 'Sümbülteber', 'magnolia': 'Manolya', 'black pepper': 'Karabiber', 'heliotrope': 'Heliotrop',
    'caramel': 'Karamel', 'apple': 'Elma', 'guaiac wood': 'Gayak ağacı', 'tobacco': 'Tütün', 'mint': 'Nane',
    'pepper': 'Biber', 'gardenia': 'Gardenya', 'moss': 'Yosun', 'plum': 'Erik', 'citruses': 'Narenciye',
    'jasmine sambac': 'Sambac yasemini', 'violet leaf': 'Menekşe yaprağı', 'violet leaves': 'Menekşe yaprağı',
    'coconut': 'Hindistan cevizi', 'ambroxan': 'Ambroxan', 'coriander': 'Kişniş', 'virginia cedar': 'Virginia sediri',
    'virginian cedar': 'Virginia sediri', 'pineapple': 'Ananas', 'olibanum': 'Günlük (olibanum)', 'honey': 'Bal',
    'lime': 'Misket limonu', 'sage': 'Adaçayı', 'green notes': 'Yeşil notalar', 'cashmere wood': 'Kaşmir ağacı',
    'osmanthus': 'Osmanthus', 'cashmeran': 'Kaşmeran', 'orchid': 'Orkide', 'almond': 'Badem', 'tangerine': 'Mandalina',
    'petitgrain': 'Petitgrain', 'cedarwood': 'Sedir', 'coffee': 'Kahve', 'clary sage': 'Misk adaçayı', 'lily': 'Zambak',
    'cypress': 'Selvi', 'orris': 'İris kökü', 'orris root': 'İris kökü', 'rosemary': 'Biberiye', 'myrrh': 'Mür',
    'myrhh': 'Mür', 'basil': 'Fesleğen', 'vanille': 'Vanilya', 'vanila': 'Vanilya', 'praline': 'Pralin', 'rum': 'Rom',
    'galbanum': 'Galbanum', 'bulgarian rose': 'Bulgar gülü', 'aldehydes': 'Aldehitler', 'floral notes': 'Çiçeksi notalar',
    'spices': 'Baharatlar', 'spicy notes': 'Baharatlı notalar', 'bitter orange': 'Turunç', 'suede': 'Süet',
    'litchi': 'Liçi', 'mimosa': 'Mimoza', 'sea notes': 'Deniz notaları', 'cloves': 'Karanfil', 'clove': 'Karanfil',
    'amberwood': 'Amber ağacı', 'sugar': 'Şeker', 'milk': 'Süt', 'apricot': 'Kayısı', 'elemi': 'Elemi',
    'elemi resin': 'Elemi reçinesi', 'juniper': 'Ardıç', 'juniper berries': 'Ardıç meyvesi', 'turkish rose': 'Türk gülü',
    'ambrette (musk mallow)': 'Amber çiçeği', 'ambrette': 'Amber çiçeği', 'fig': 'İncir', 'lotus': 'Lotus',
    'artemisia': 'Pelin', 'damask rose': 'Şam gülü', 'madagascar vanilla': 'Madagaskar vanilyası', 'tea': 'Çay',
    'red berries': 'Kırmızı meyveler', 'frangipani': 'Frangipani', 'white flowers': 'Beyaz çiçekler',
    'cypriol oil or nagarmotha': 'Nagarmotha', 'cypriol': 'Nagarmotha', 'strawberry': 'Çilek', 'styrax': 'Sığla',
    'passionfruit': 'Çarkıfelek meyvesi', 'cacao': 'Kakao', 'cocoa': 'Kakao', 'honeysuckle': 'Hanımeli',
    'mandarin': 'Mandalina', 'mango': 'Mango', 'cherry': 'Kiraz', 'green apple': 'Yeşil elma', 'rhubarb': 'Ravent',
    'melon': 'Kavun', 'carnation': 'Karanfil çiçeği', 'haitian vetiver': 'Haiti vetiveri', 'amalfi lemon': 'Amalfi limonu',
    'cyclamen': 'Siklamen', 'birch': 'Huş ağacı', 'blackberry': 'Böğürtlen', 'blood orange': 'Kan portakalı',
    'bourbon vanilla': 'Bourbon vanilyası', 'water lily': 'Nilüfer', 'cumin': 'Kimyon', 'yuzu': 'Yuzu',
    'red apple': 'Kırmızı elma', 'thyme': 'Kekik', 'fruity notes': 'Meyveli notalar', 'calabrian bergamot': 'Kalabriya bergamotu',
    'african orange flower': 'Afrika portakal çiçeği', 'narcissus': 'Nergis', 'salt': 'Tuz', 'davana': 'Davana',
    'hazelnut': 'Fındık', 'immortelle': 'Ölmez çiçek', 'angelica': 'Melek otu', 'tiare flower': 'Tiare çiçeği',
    'tonka': 'Tonka fasulyesi', 'green tea': 'Yeşil çay', 'water notes': 'Su notaları', 'watery notes': 'Su notaları',
    'papyrus': 'Papirüs', 'powdery notes': 'Pudralı notalar', 'star anise': 'Yıldız anason', 'oak': 'Meşe',
    'whipped cream': 'Krem şanti', 'anise': 'Anason', 'lilac': 'Leylak', 'mate': 'Mate', 'peru balsam': 'Peru balsamı',
    'licorice': 'Meyan kökü', 'opoponax': 'Opoponaks', 'citron': 'Ağaç kavunu', 'black tea': 'Siyah çay',
    'castoreum': 'Kastoreum', 'green leaves': 'Yeşil yapraklar', 'atlas cedar': 'Atlas sediri', 'pomegranate': 'Nar',
    'frankincense': 'Tütsü (günlük)', 'smoke': 'Duman', 'tolu balsam': 'Tolu balsamı', 'balsam fir': 'Köknar balsamı',
    'chocolate': 'Çikolata', 'chamomile': 'Papatya', 'sicilian lemon': 'Sicilya limonu', 'watermelon': 'Karpuz',
    'pink grapefruit': 'Pembe greyfurt', 'fig leaf': 'İncir yaprağı', 'egyptian jasmine': 'Mısır yasemini',
    'coumarin': 'Kumarin', 'marshmallow': 'Marshmallow', 'palisander rosewood': 'Gül ağacı', 'marigold': 'Kadife çiçeği',
    'white woods': 'Beyaz ağaçlar', 'vanilla absolute': 'Vanilya absolü', 'civet': 'Misk kedisi', 'may rose': 'Mayıs gülü',
    'rose de mai': 'Mayıs gülü', 'white amber': 'Beyaz amber', 'green mandarin': 'Yeşil mandalina',
    'red currant': 'Kırmızı frenk üzümü', 'coconut milk': 'Hindistan cevizi sütü', 'ozonic notes': 'Ozonik notalar',
    'dark chocolate': 'Bitter çikolata', 'sweet notes': 'Tatlı notalar', 'eucalyptus': 'Okaliptüs',
    'clementine': 'Klementin', 'driftwood': 'Deniz odunu', 'sea water': 'Deniz suyu', 'sea salt': 'Deniz tuzu',
    'hyacinth': 'Sümbül', 'caraway': 'Karaman kimyonu', 'cognac': 'Konyak', 'pistachio': 'Antep fıstığı',
    'red fruits': 'Kırmızı meyveler', 'hedione': 'Hedione', 'vanilla orchid': 'Vanilya orkidesi', 'amyris': 'Amyris',
    'australian sandalwood': 'Avustralya sandal ağacı', 'hay': 'Kuru ot', 'resins': 'Reçineler', 'resin': 'Reçine',
    'cherry blossom': 'Kiraz çiçeği', 'pomelo': 'Pomelo', 'white pepper': 'Beyaz biber', 'solar notes': 'Güneş notaları',
    'nectarine': 'Nektarin', 'sichuan pepper': 'Sichuan biberi', 'italian lemon': 'İtalyan limonu',
    'brown sugar': 'Esmer şeker', 'grass': 'Çimen', 'bamboo': 'Bambu', 'beeswax': 'Balmumu', 'pine': 'Çam',
    'pine tree': 'Çam', 'cotton candy': 'Pamuk şeker', 'akigalawood': 'Akigala ağacı', 'hibiscus': 'Hibiskus',
    'iso e super': 'Iso E Super', 'carrot seeds': 'Havuç tohumu', 'wormwood': 'Pelin otu', 'guava': 'Guava',
    'sweet orange': 'Tatlı portakal', 'fruits': 'Meyveler', 'seaweed': 'Deniz yosunu', 'french labdanum': 'Fransız labdanumu',
    'blueberry': 'Yaban mersini', 'dried fruits': 'Kuru meyveler', 'fir': 'Köknar', 'sicilian bergamot': 'Sicilya bergamotu',
    'flowers': 'Çiçekler', 'dates': 'Hurma', 'teak wood': 'Tik ağacı', 'italian mandarin': 'İtalyan mandalinası',
    'whiskey': 'Viski', 'tobacco leaf': 'Tütün yaprağı', 'siam benzoin': 'Siyam benzoini', 'lemon verbena': 'Limon otu',
    'peach blossom': 'Şeftali çiçeği', 'white peach': 'Beyaz şeftali', 'precious woods': 'Değerli ağaçlar',
    'ivy': 'Sarmaşık', 'herbal notes': 'Bitkisel notalar', 'brazilian rosewood': 'Brezilya gül ağacı',
    'bitter almond': 'Acı badem', 'bay leaf': 'Defne yaprağı', 'rice': 'Pirinç', 'vanilla bean': 'Vanilya çubuğu',
    'myrtle': 'Mersin', 'hawthorn': 'Alıç', 'pimento': 'Yenibahar', 'rose petals': 'Gül yaprakları',
    'cacao pod': 'Kakao kabuğu', 'tarragon': 'Tarhun', 'gurjan balsam': 'Gurjan balsamı', 'apple blossom': 'Elma çiçeği',
    'champaca': 'Çampaka', 'mastic or lentisque': 'Sakız', 'vetyver': 'Vetiver', 'white rose': 'Beyaz gül',
    'wild berries': 'Yaban meyveleri', 'white tea': 'Beyaz çay', 'champagne': 'Şampanya', 'mineral notes': 'Mineral notalar',
    'palo santo': 'Palo santo', 'cassia': 'Çin tarçını', 'toffee': 'Tofi', 'animal notes': 'Hayvansı notalar',
    'indian jasmine': 'Hint yasemini', 'cucumber': 'Salatalık', 'peppermint': 'Nane', 'white chocolate': 'Beyaz çikolata',
    'almond blossom': 'Badem çiçeği', 'quince': 'Ayva', 'passion flower': 'Çarkıfelek çiçeği', 'mahogany': 'Maun',
    'kumquat': 'Kamkat', 'butter': 'Tereyağı', 'granny smith apple': 'Yeşil elma', 'cranberry': 'Turna yemişi',
    'metallic notes': 'Metalik notalar', 'cannabis': 'Kenevir', 'sandalowood': 'Sandal ağacı', 'white cedar extract': 'Beyaz sedir',
    'indonesian patchouli leaf': 'Endonezya paçulisi', 'vanilla absolute ': 'Vanilya absolü', 'lychee': 'Liçi',
    'plum blossom': 'Erik çiçeği', 'cinnamon bark': 'Tarçın kabuğu', 'leather notes': 'Deri notaları',
    'marine notes': 'Deniz notaları', 'aquatic notes': 'Su notaları', 'banana': 'Muz', 'lemon zest': 'Limon kabuğu',
    'orange peel': 'Portakal kabuğu', 'fir resin': 'Köknar reçinesi', 'tobacco blossom': 'Tütün çiçeği',
    'bellflower': 'Çan çiçeği', 'water jasmine': 'Su yasemini', 'paprika': 'Kırmızı biber', 'tonka beans': 'Tonka fasulyesi',
    'jasmine tea': 'Yasemin çayı', 'sugar cane': 'Şeker kamışı', 'vanilla extract': 'Vanilya özü', 'lemon tree': 'Limon ağacı', 'nutmeg flower': 'Muskat çiçeği', 'caviar': 'Havyar', 'gin': 'Cin', 'vodka': 'Votka',
}

# Full category taxonomy: how a perfume is generally perceived, derived from community-voted accords,
# rating, longevity and sillage. Groups are shown as filter sections in the app.
TAXONOMY = [
    ('Kullanım', ['Günlük', 'Ofis', 'Date', 'Parti', 'Özel Gün', 'Spor', 'Tatil']),
    ('Mevsim', ['İlkbahar', 'Yaz', 'Sonbahar', 'Kış', 'Dört Mevsim']),
    ('Zaman', ['Gündüz', 'Gece']),
    ('Karakter', ['Fresh', 'Narenciye', 'Aquatik', 'Aromatik', 'Yeşil', 'Çiçeksi', 'Meyveli', 'Tatlı', 'Gurme',
                  'Vanilyalı', 'Amber', 'Oryantal', 'Baharatlı', 'Odunsu', 'Ud', 'Deri', 'Tütün', 'Misk', 'Pudralı', 'İsli']),
    ('Etki', ['Seksi', 'İltifat Toplayan', 'Temiz', 'Kör Alım Güvenli']),
    ('Yoğunluk', ['Hafif', 'Orta', 'Güçlü', 'Uzun Kalıcı']),
    ('Popülerlik', ['Çok Popüler', 'Yüksek Puanlı', 'Klasik', 'Yeni Çıkan']),
    ('Cinsiyet', ['Erkek', 'Kadın', 'Unisex']),
]
LABELS = [l for _, ls in TAXONOMY for l in ls]
LABEL_IDX = {l: i for i, l in enumerate(LABELS)}

USE_RULES = {
    'Günlük': {'fresh': 1, 'citrus': 1, 'aromatic': .8, 'green': .8, 'fresh spicy': .7, 'musky': .5, 'aquatic': .8,
               'ozonic': .7, 'lavender': .6, 'herbal': .6, 'soapy': .7, 'powdery': .3, 'fruity': .4, 'woody': .3},
    'Ofis': {'aromatic': 1, 'woody': .5, 'citrus': .6, 'fresh spicy': .7, 'iris': .8, 'powdery': .6, 'lavender': .7,
             'soapy': .8, 'mossy': .6, 'musky': .4, 'green': .4, 'violet': .5},
    'Spor': {'aquatic': 1.1, 'marine': 1.1, 'ozonic': 1, 'citrus': .8, 'fresh': .9, 'green': .5, 'salty': .7, 'fresh spicy': .4},
    'Tatil': {'tropical': 1.2, 'coconut': 1.2, 'marine': .9, 'salty': 1, 'aquatic': .7, 'citrus': .5, 'fruity': .5, 'yellow floral': .6},
    'Parti': {'sweet': .8, 'fruity': .6, 'vanilla': .6, 'cherry': .8, 'rum': .8, 'caramel': .6, 'warm spicy': .4,
              'amber': .4, 'tropical': .3, 'coffee': .5},
    'Date': {'vanilla': .9, 'sweet': .7, 'rose': .7, 'white floral': .7, 'warm spicy': .5, 'amber': .5, 'lactonic': .8,
             'powdery': .4, 'fruity': .4, 'cherry': .9, 'iris': .4, 'tuberose': .6, 'almond': .6, 'caramel': .5, 'musky': .3},
    'Özel Gün': {'oud': .9, 'white floral': .6, 'rose': .6, 'iris': .7, 'amber': .5, 'tuberose': .6, 'aldehydic': .9,
                 'balsamic': .5, 'leather': .5, 'champagne': 1},
}
SEXY = {'animalic': 1, 'vanilla': .7, 'amber': .6, 'leather': .7, 'tuberose': .8, 'oud': .6, 'musky': .5,
        'cacao': .7, 'caramel': .6, 'honey': .7, 'rum': .7, 'cherry': .7, 'coffee': .6, 'sweet': .4, 'tobacco': .5}
CHARACTER = {
    'Fresh': ['fresh', 'ozonic'], 'Narenciye': ['citrus'], 'Aquatik': ['aquatic', 'marine', 'salty'],
    'Aromatik': ['aromatic', 'lavender', 'herbal'], 'Yeşil': ['green'],
    'Çiçeksi': ['floral', 'white floral', 'rose', 'yellow floral', 'tuberose', 'violet'],
    'Meyveli': ['fruity', 'tropical', 'cherry'], 'Tatlı': ['sweet', 'honey', 'caramel'],
    'Gurme': ['caramel', 'cacao', 'chocolate', 'coffee', 'almond', 'nutty', 'lactonic', 'coconut', 'rum'],
    'Vanilyalı': ['vanilla'], 'Amber': ['amber', 'balsamic'],
    'Baharatlı': ['warm spicy', 'fresh spicy', 'soft spicy', 'cinnamon'],
    'Odunsu': ['woody', 'mossy', 'conifer', 'patchouli', 'earthy'], 'Ud': ['oud'], 'Deri': ['leather'],
    'Tütün': ['tobacco'], 'Misk': ['musky', 'animalic'], 'Pudralı': ['powdery', 'iris'], 'İsli': ['smoky'],
}

def categories(accords, lon, sil5, votes, rating, year, g):
    w = {a: v / 100 for a, v in accords}
    top3 = {a for a, _ in accords[:3]}
    v = lambda *ks: sum(w.get(k, 0) for k in ks)
    out = []
    # Kullanım
    sc = {c: sum(w.get(a, 0) * k for a, k in r.items()) for c, r in USE_RULES.items()}
    if lon >= 4: sc['Özel Gün'] += .3
    if lon and lon < 2.8: sc['Günlük'] += .2
    if sil5 >= 4: sc['Parti'] += .3
    ranked = sorted(sc.items(), key=lambda x: -x[1])
    out += [c for c, s in ranked if s >= max(.7, ranked[0][1] * .55)][:3] or [ranked[0][0]]
    # Mevsim
    seasons = {
        'İlkbahar': v('floral', 'white floral', 'rose', 'green', 'fruity', 'powdery', 'yellow floral') + .5 * v('citrus'),
        'Yaz': v('fresh', 'citrus', 'aquatic', 'marine', 'ozonic', 'tropical', 'coconut', 'salty') + .5 * v('fruity', 'green'),
        'Sonbahar': v('woody', 'fresh spicy', 'soft spicy', 'patchouli', 'leather', 'earthy') + .5 * v('amber', 'warm spicy'),
        'Kış': v('warm spicy', 'vanilla', 'amber', 'tobacco', 'oud', 'balsamic', 'caramel', 'smoky', 'cinnamon', 'leather', 'rum'),
    }
    m = max(seasons.values()) or 1
    ss = [s for s, x in seasons.items() if x >= m * .6]
    out += ss
    if len(ss) >= 3: out.append('Dört Mevsim')
    # Zaman
    day = v('fresh', 'citrus', 'aquatic', 'marine', 'green', 'aromatic', 'soapy', 'ozonic', 'fresh spicy')
    night = v('amber', 'vanilla', 'sweet', 'warm spicy', 'oud', 'leather', 'animalic', 'tobacco', 'balsamic', 'smoky')
    if day >= night * .7: out.append('Gündüz')
    if night >= day * .7: out.append('Gece')
    # Karakter
    for label, keys in CHARACTER.items():
        if any(w.get(k, 0) >= .45 or k in top3 for k in keys): out.append(label)
    if (w.get('amber', 0) >= .4 and w.get('warm spicy', 0) >= .4) or w.get('balsamic', 0) >= .5 or w.get('oud', 0) >= .5:
        out.append('Oryantal')
    # Etki
    if sum(w.get(a, 0) * k for a, k in SEXY.items()) >= .95: out.append('Seksi')
    if max(w.get('sweet', 0), w.get('vanilla', 0), w.get('fruity', 0)) >= .6 and sil5 >= 3 and votes >= 1500 and rating >= 3.9:
        out.append('İltifat Toplayan')
    if w.get('soapy', 0) >= .3 or w.get('aldehydic', 0) >= .4 or (w.get('musky', 0) >= .5 and max(w.get('fresh', 0), w.get('powdery', 0)) >= .4):
        out.append('Temiz')
    if rating >= 4.0 and votes >= 3000: out.append('Kör Alım Güvenli')
    # Yoğunluk
    strength = (lon + sil5) / 2
    out.append('Hafif' if strength < 2.6 else 'Orta' if strength < 3.4 else 'Güçlü')
    if lon >= 3.9: out.append('Uzun Kalıcı')
    # Popülerlik
    if votes >= 10000: out.append('Çok Popüler')
    if rating >= 4.2 and votes >= 300: out.append('Yüksek Puanlı')
    if year and year <= 2005 and votes >= 2000: out.append('Klasik')
    if year and year >= 2025: out.append('Yeni Çıkan')
    out.append({'E': 'Erkek', 'K': 'Kadın'}.get(g, 'Unisex'))
    seen, res = set(), []
    for c in out:
        if c not in seen:
            seen.add(c); res.append(LABEL_IDX[c])
    return res

def tr_note(n):
    n = n.strip()
    return NOTE_TR.get(n.lower(), n)

def parse_weighted(s, limit):
    out = []
    for part in (s or '').split('|'):
        if not part: continue
        name, _, w = part.rpartition(':')
        if not name: name, w = part, '0'
        out.append((name.strip(), float(w or 0)))
    return out[:limit]

def norm_brand(s):
    s = s.lower().replace('&', 'and')
    s = re.sub(r'\bparfums?\b|\bperfumes?\b|\bfragrances?\b|\bparis\b', '', s)
    return re.sub(r'[^a-z0-9]', '', s)

def build_world(src):
    if src and os.path.exists(src):
        f = open(src, encoding='utf-8', errors='replace')
    else:
        print('downloading', SRC_URL)
        f = io.TextIOWrapper(urllib.request.urlopen(SRC_URL, timeout=120), encoding='utf-8', errors='replace')
    items = []
    for x in csv.DictReader(f):
        try:
            votes = int(float(x['vote_count'] or 0))
        except ValueError:
            continue
        if votes < 50 or not x['name']: continue
        acc = parse_weighted(x['accords'], 8)
        lon = float(x['longevity_avg'] or 0)
        sil = float(x['sillage_avg'] or 0)
        top = [tr_note(n) for n, _ in parse_weighted(x['notes_top'], 7)]
        mid = [tr_note(n) for n, _ in parse_weighted(x['notes_middle'], 7)]
        base = [tr_note(n) for n, _ in parse_weighted(x['notes_base'], 7)]
        if not (top or mid or base):
            mid = [tr_note(n) for n, _ in parse_weighted(x['notes_flat'], 10)]
        fam = []
        for a, _ in acc[:5]:
            t = ACCORD_TR.get(a, a.capitalize())
            if t not in fam: fam.append(t)
        g = {'male': 'E', 'female': 'K'}.get(x['gender'], 'U')
        year = x['year'].split('.')[0] if x['year'] and x['year'] != 'nan' else ''
        year = int(year) if year.isdigit() else 0
        rating = float(x['rating_avg'] or 0)
        sil5 = min(5, sil * 5 / 4)
        items.append([
            int(x['id']), x['name'].strip(), x['brand'].strip(), year, g, votes,
            round(lon), round(sil5), fam, top, mid, base, categories(acc, lon, sil5, votes, rating, year, g), round(rating, 2),
        ])
    items.sort(key=lambda i: -i[5])
    return items

def fetch_boyner(local=None):
    if local:
        html = open(local, encoding='utf-8').read()
    else:
        req = urllib.request.Request(BOYNER_URL, headers={'User-Agent': UA, 'Accept-Language': 'tr-TR,tr;q=0.9'})
        html = urllib.request.urlopen(req, timeout=40).read().decode('utf-8', 'replace')
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    if not m: raise RuntimeError('Boyner page not readable (bot protection?)')
    q = json.loads(m.group(1))['props']['pageProps']['initialState']['dsListingSearchService']['queries']
    brands, top = [], []
    for k, v in q.items():
        data = (v or {}).get('data') or {}
        if k.startswith('getFilters'):
            for flt in data.get('Filters') or []:
                attrs = flt.get('Attributes') or []
                if attrs and str(attrs[0].get('Id', '')).startswith('marka='):
                    for a in attrs:
                        brands.append({'n': a.get('DisplayName') or a.get('Name'), 'k': norm_brand(a.get('DisplayName') or a.get('Name') or ''),
                                       'u': 'https://www.boyner.com.tr/' + (a.get('SpecialLink') or '').lstrip('/')})
        if k.startswith('getProducts'):
            for p in data.get('Products') or []:
                img = next((md['Url'] for md in p.get('Medias') or [] if md.get('IsDefault')), None)
                price = (p.get('PriceInfo') or {}).get('Price')
                top.append({'t': p.get('EditorName') or p.get('Title'), 'b': p.get('Brand'), 'g': p.get('Gender'),
                            'img': img, 'p': price, 'u': 'https://www.boyner.com.tr/' + p.get('Url', '')})
    if not brands: raise RuntimeError('no brands found')
    return {'updated': datetime.date.today().isoformat(), 'brands': brands, 'top': top}

def main():
    src = sys.argv[sys.argv.index('--src') + 1] if '--src' in sys.argv else None
    os.makedirs(OUT, exist_ok=True)
    items = build_world(src)
    with open(os.path.join(OUT, 'catalog.json'), 'w', encoding='utf-8') as f:
        json.dump({'v': datetime.date.today().isoformat(), 'n': len(items), 'tax': TAXONOMY, 'labels': LABELS, 'items': items}, f, ensure_ascii=False, separators=(',', ':'))
    print('catalog', len(items))
    try:
        b = fetch_boyner(sys.argv[sys.argv.index('--boyner-html') + 1] if '--boyner-html' in sys.argv else None)
        with open(os.path.join(OUT, 'boyner.json'), 'w', encoding='utf-8') as f:
            json.dump(b, f, ensure_ascii=False, separators=(',', ':'))
        print('boyner brands', len(b['brands']), 'top', len(b['top']))
    except Exception as e:
        print('boyner skipped:', e)

if __name__ == '__main__':
    main()

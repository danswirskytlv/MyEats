#!/usr/bin/env python3
"""
MyEats — one-shot seeding script.

Creates 15 demo users in Firebase Auth, uploads a real food/drink photo per
recipe to Firebase Storage, and writes 33 recipes + profiles + comment threads
to the Realtime Database. Ratings and Chef replies are pre-filled so the app
feels alive in the demo.

Run from anywhere (uses paths relative to this file):
    python3 seed_all.py

Requires: Python 3 (standard library only). Reads the API key from
app/google-services.json — run only after that file is in place.
"""

import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# macOS Python often ships without SSL root certificates. Try the normal
# verified context first; if the handshake fails, fall back to unverified
# (fine for this one-time seeding script).
def _make_ssl_context():
    ctx = ssl.create_default_context()
    try:
        req = urllib.request.Request("https://www.googleapis.com/", method="HEAD")
        urllib.request.urlopen(req, timeout=10, context=ctx)
        return ctx
    except ssl.SSLError:
        pass
    except urllib.error.URLError as e:
        if not isinstance(getattr(e, "reason", None), ssl.SSLError):
            return ctx  # network error, not a cert problem — keep verification
    except Exception:
        return ctx
    print("NOTE: SSL certificates missing on this Python — continuing without verification.\n")
    return ssl._create_unverified_context()


SSL_CTX = _make_ssl_context()

HERE = os.path.dirname(os.path.abspath(__file__))
GS_PATH = os.path.join(HERE, "..", "app", "google-services.json")

PASSWORD = "123456"  # same easy password for all demo users

# (name, email, bio, pravatar image id)
USERS = [
    ("Noa Levi", "noa@myeats.com", "Home cook from Tel Aviv. Weekend brunch is my religion.", 47),
    ("Omer Cohen", "omer@myeats.com", "Wok enthusiast. If it can be stir-fried, I will stir-fry it.", 12),
    ("Maya Mizrahi", "maya@myeats.com", "Pasta first, questions later. Learning nonna-level carbonara.", 44),
    ("Lior Peretz", "lior@myeats.com", "Slow food believer — soups, stews and everything caramelized.", 15),
    ("Tamar Biton", "tamar@myeats.com", "Pastry student. I measure flour by the gram and joy by the brownie.", 32),
    ("Yuval Katz", "yuval@myeats.com", "Meal-prep nerd. Feeding a hungry family of five every week.", 53),
    ("Shira Azulay", "shira@myeats.com", "Pizza fridays forever. Sourdough starter named Shimon.", 26),
    ("Daniel Friedman", "daniel@myeats.com", "Fish and seafood lover, big on Japanese home cooking.", 68),
    ("Roni Shapiro", "roni@myeats.com", "Plant-forward cooking with Middle Eastern roots.", 20),
    ("Itay Golan", "itay@myeats.com", "Street food hunter recreating market classics at home.", 59),
    ("Adam Rosen", "adam@myeats.com", "One-dish wonder. When I nail a recipe, I never let it go.", 33),
    ("Yael Stern", "yael@myeats.com", "Balkan grandma energy. My moussaka has a waiting list.", 45),
    ("Nadav Barak", "nadav@myeats.com", "Weekend paella guy — big pan, big flame, big family.", 65),
    ("Ella Navon", "ella@myeats.com", "French-trained home cook. Butter is a vegetable.", 24),
    ("Tom Harel", "tom@myeats.com", "Ex-bartender turned home chef. Dinner and a drink, always.", 51),
]

# (owner index, title, [categories], TheMealDB search term, ingredients, steps)
RECIPES = [
    (0, "Classic Shakshuka", ["Breakfast"], "shakshuka",
     "4 eggs\n5 ripe tomatoes\n1 onion\n1 red pepper\n2 garlic cloves\n1 tsp paprika\nSalt and pepper",
     "1. Fry the chopped onion and pepper until soft.\n2. Add garlic, paprika and diced tomatoes; simmer 10 minutes.\n3. Make wells and crack in the eggs.\n4. Cover and cook until the whites set."),
    (0, "Fluffy Pancakes", ["Breakfast"], "pancakes",
     "2 cups flour\n2 eggs\n1.5 cups milk\n3 tbsp sugar\n1 tbsp baking powder\nPinch of salt\nButter for frying",
     "1. Whisk the dry ingredients.\n2. Add eggs and milk; mix to a smooth batter.\n3. Fry ladles of batter in butter until bubbles form, flip.\n4. Serve with maple syrup."),
    (1, "Pad Thai", ["Lunch", "Dinner"], "pad thai",
     "200g rice noodles\n2 eggs\n150g tofu or chicken\nBean sprouts\n2 tbsp tamarind paste\n2 tbsp fish sauce\nPeanuts, lime",
     "1. Soak the noodles in warm water.\n2. Stir-fry the protein, push aside and scramble the eggs.\n3. Add noodles and sauce; toss on high heat.\n4. Top with sprouts, peanuts and lime."),
    (1, "Beef Stroganoff", ["Dinner"], "beef stroganoff",
     "500g beef strips\n250g mushrooms\n1 onion\n200ml sour cream\n1 tbsp mustard\nButter, paprika, salt",
     "1. Sear the beef quickly and set aside.\n2. Soften onion and mushrooms in butter.\n3. Add cream, mustard and paprika; return the beef.\n4. Simmer 5 minutes and serve over noodles."),
    (2, "Creamy Carbonara", ["Dinner"], "carbonara",
     "400g spaghetti\n150g pancetta\n3 egg yolks\n60g parmesan\nBlack pepper",
     "1. Cook the pasta al dente, keep a cup of pasta water.\n2. Crisp the pancetta.\n3. Off the heat, toss pasta with yolks, cheese and a splash of water.\n4. Season generously with pepper."),
    (2, "Greek Salad", ["Salad", "Lunch"], "greek salad",
     "3 tomatoes\n1 cucumber\n1 red onion\n150g feta\nBlack olives\nOlive oil, oregano, lemon",
     "1. Chop the vegetables into chunks.\n2. Add olives and cubed feta.\n3. Dress with olive oil, lemon and oregano; toss gently."),
    (3, "Chicken Fajitas", ["Lunch", "Dinner"], "fajita",
     "2 chicken breasts\n2 peppers\n1 onion\nFajita spice mix\nTortillas\nSour cream, lime",
     "1. Slice chicken and vegetables into strips.\n2. Stir-fry on high heat with the spices.\n3. Serve sizzling with warm tortillas and sour cream."),
    (3, "French Onion Soup", ["Soup", "Dinner"], "french onion soup",
     "6 large onions\n1L beef stock\n2 tbsp butter\n1 tbsp flour\nBaguette slices\nGruyere cheese",
     "1. Caramelize the onions slowly in butter (30 min).\n2. Dust with flour, add stock and simmer 20 minutes.\n3. Ladle into bowls, top with baguette and cheese, grill until golden."),
    (4, "Chocolate Brownies", ["Dessert"], "brownies",
     "200g dark chocolate\n150g butter\n3 eggs\n1 cup sugar\n0.5 cup flour\nPinch of salt",
     "1. Melt chocolate and butter together.\n2. Whisk eggs and sugar until fluffy; fold in the chocolate.\n3. Fold in flour and salt.\n4. Bake 25 minutes at 175°C — centre should stay fudgy."),
    (4, "Banana Pancakes", ["Breakfast", "Dessert"], "banana pancakes",
     "2 ripe bananas\n2 eggs\n0.5 cup oats\n1 tsp cinnamon\nButter for frying",
     "1. Mash the bananas.\n2. Mix in eggs, oats and cinnamon.\n3. Fry small pancakes 2 minutes per side.\n4. Serve with honey."),
    (5, "Chicken Noodle Soup", ["Soup"], "chicken soup",
     "2 chicken thighs\n2 carrots\n2 celery sticks\n1 onion\nEgg noodles\nBay leaf, dill, salt",
     "1. Simmer chicken with vegetables and bay leaf for 40 minutes.\n2. Shred the chicken back into the pot.\n3. Add noodles and cook 8 minutes.\n4. Finish with fresh dill."),
    (5, "Beef Tacos", ["Lunch", "Dinner"], "taco",
     "400g ground beef\nTaco shells\n1 onion\nTaco seasoning\nLettuce, tomato, cheddar\nSalsa",
     "1. Brown the beef with onion and seasoning.\n2. Warm the shells.\n3. Assemble with lettuce, tomato, cheese and salsa."),
    (6, "Margherita Pizza", ["Lunch", "Dinner"], "pizza",
     "Pizza dough\n200g tomato sauce\n250g mozzarella\nFresh basil\nOlive oil",
     "1. Stretch the dough thin.\n2. Spread sauce, tear over the mozzarella.\n3. Bake at max heat 8-10 minutes.\n4. Finish with basil and olive oil."),
    (6, "Caesar Salad", ["Salad", "Lunch"], "quinoa",
     "2 romaine hearts\nCroutons\nParmesan shavings\n1 egg yolk\n2 anchovies\nGarlic, lemon, olive oil",
     "1. Whisk yolk, anchovies, garlic and lemon into a dressing, stream in oil.\n2. Toss the leaves with dressing.\n3. Top with croutons and parmesan."),
    (7, "Salmon Teriyaki", ["Dinner"], "salmon",
     "2 salmon fillets\n3 tbsp soy sauce\n2 tbsp mirin\n1 tbsp honey\nGinger, garlic\nSesame seeds",
     "1. Mix soy, mirin, honey, ginger and garlic.\n2. Sear the salmon skin-side down.\n3. Pour in the sauce and glaze 3 minutes.\n4. Sprinkle sesame and serve with rice."),
    (7, "Mushroom Risotto", ["Dinner"], "risotto",
     "300g arborio rice\n300g mushrooms\n1L hot stock\n1 onion\nWhite wine\nParmesan, butter",
     "1. Soften onion, add rice and toast 2 minutes.\n2. Splash of wine, then add stock ladle by ladle, stirring.\n3. Fry mushrooms separately and fold in.\n4. Finish with butter and parmesan."),
    (8, "Hummus Bowl", ["Lunch", "Snack"], "hummus",
     "2 cups cooked chickpeas\n3 tbsp tahini\nJuice of 1 lemon\n2 garlic cloves\nOlive oil, cumin, paprika",
     "1. Blend chickpeas, tahini, lemon and garlic until silky.\n2. Loosen with iced water.\n3. Swirl onto a plate, drizzle oil and dust with paprika.\n4. Serve with warm pita."),
    (8, "Apple Pie", ["Dessert"], "apple pie",
     "6 apples\n2 pie crusts\n0.5 cup sugar\n1 tsp cinnamon\n1 tbsp butter\n1 egg for brushing",
     "1. Toss sliced apples with sugar and cinnamon.\n2. Fill the bottom crust, dot with butter, cover and crimp.\n3. Brush with egg, slit the top.\n4. Bake 45 minutes at 190°C."),
    (9, "Falafel Pita", ["Lunch", "Dinner"], "falafel",
     "2 cups soaked chickpeas\n1 onion\nParsley and coriander\n2 tsp cumin\nPita, tahini, pickles",
     "1. Blitz chickpeas with herbs, onion and spices.\n2. Rest the mix 30 minutes, form balls.\n3. Deep-fry until deep golden.\n4. Stuff pitas with falafel, salad and tahini."),
    (9, "Berry Cheesecake", ["Dessert"], "cheesecake",
     "250g biscuits\n100g butter\n600g cream cheese\n150g sugar\n3 eggs\nMixed berries",
     "1. Press crushed biscuits + butter into a tin.\n2. Beat cheese, sugar and eggs; pour over the base.\n3. Bake 50 minutes at 160°C, cool completely.\n4. Top with berries."),
    (10, "Homemade Lasagne", ["Dinner"], "lasagne",
     "500g minced beef\n12 lasagne sheets\n800g chopped tomatoes\n1 onion\n2 carrots\n125g mozzarella\n400ml creme fraiche\nParmesan",
     "1. Brown the beef with onion and carrot, add tomatoes and simmer 20 minutes.\n2. Layer ragu and pasta sheets in a dish.\n3. Pour over creme fraiche, top with mozzarella and parmesan.\n4. Bake 30 minutes at 200°C until golden and bubbling."),
    (11, "Greek Moussaka", ["Dinner"], "moussaka",
     "500g minced beef\n1 large aubergine\n150g Greek yogurt\n1 egg\n3 tbsp parmesan\n400g tomatoes\n350g potatoes",
     "1. Brown the beef, stir in tomatoes and sliced potatoes.\n2. Soften the aubergine and layer on top.\n3. Mix yogurt, egg and parmesan; pour over.\n4. Grill until the topping sets golden."),
    (12, "Seafood Paella", ["Lunch", "Dinner"], "paella",
     "250g paella rice\n10 tiger prawns\n500g mussels\n150g chorizo\n1 onion\nSaffron\nGarlic, tomatoes, lemon",
     "1. Make a quick stock from the prawn heads and saffron.\n2. Fry chorizo, onion and garlic; add the rice.\n3. Add stock and boil, then simmer without stirring.\n4. Tuck in the seafood for the last minutes and rest before serving."),
    (13, "Provençal Ratatouille", ["Dinner"], "ratatouille",
     "2 aubergines\n4 courgettes\n2 peppers\n4 large tomatoes\n1 onion\nGarlic, basil, olive oil\nRed wine vinegar",
     "1. Cut all vegetables into chunky pieces.\n2. Brown the aubergine, courgette and pepper in batches.\n3. Soften onion and garlic, add tomatoes and vinegar.\n4. Return everything to the pan and simmer with basil."),
    (13, "Molten Chocolate Cake", ["Dessert"], "chocolate",
     "200g dark chocolate\n200g butter\n4 eggs\n200g sugar\n100g flour\nPinch of salt",
     "1. Melt chocolate and butter.\n2. Whisk eggs and sugar until pale, fold in the chocolate.\n3. Fold in flour and divide into ramekins.\n4. Bake 12 minutes at 200°C — the middle must stay liquid."),
    (13, "Creamy Fish Pie", ["Dinner"], "fish pie",
     "600g white fish\n1kg potatoes\n500ml milk\n50g butter\n50g flour\nHandful of peas\nCheddar for topping",
     "1. Poach the fish in milk, then flake.\n2. Make a white sauce with butter, flour and the poaching milk.\n3. Mix fish, sauce and peas in a dish; top with mash.\n4. Bake 30 minutes at 200°C until golden."),
    (13, "Crispy Breakfast Potatoes", ["Breakfast"], "breakfast",
     "800g potatoes\n2 tbsp olive oil\n1 tsp paprika\n2 spring onions\nSalt and pepper",
     "1. Parboil diced potatoes for 5 minutes.\n2. Toss with oil and paprika.\n3. Roast 30 minutes at 220°C, shaking halfway.\n4. Finish with spring onions and flaky salt."),
    (14, "Classic Mojito", ["Drink"], "drink:mojito",
     "60ml white rum\nJuice of 1 lime\n2 tsp sugar\n8 mint leaves\nSoda water\nCrushed ice",
     "1. Muddle the mint with sugar and lime juice.\n2. Fill the glass with crushed ice.\n3. Pour in the rum and top with soda water.\n4. Garnish with mint and serve with a straw."),
    (14, "Spaghetti Bolognese", ["Dinner"], "bolognese",
     "400g spaghetti\n500g minced beef\n1 onion\n2 carrots\n800g chopped tomatoes\nRed wine\nParmesan",
     "1. Brown the beef with onion and carrot.\n2. Add a splash of wine and the tomatoes; simmer 40 minutes.\n3. Cook the spaghetti al dente.\n4. Toss together and shower with parmesan."),
    (14, "Fragrant Chicken Curry", ["Dinner"], "curry",
     "600g chicken thighs\n1 onion\n2 tbsp curry paste\n400ml coconut milk\nGinger, garlic\nCoriander, rice to serve",
     "1. Brown the chicken pieces.\n2. Soften onion, ginger and garlic; add the paste.\n3. Pour in coconut milk and simmer 25 minutes.\n4. Scatter coriander and serve over rice."),
    (14, "Slow Beef Stew", ["Dinner"], "stew",
     "800g stewing beef\n3 carrots\n2 onions\n500ml beef stock\n2 tbsp flour\nThyme, bay leaf",
     "1. Toss the beef in flour and brown in batches.\n2. Soften the vegetables, return the beef.\n3. Add stock and herbs; cover.\n4. Simmer gently for 2.5 hours until falling apart."),
    (14, "Penne Arrabiata", ["Lunch", "Dinner"], "arrabiata",
     "400g penne\n800g chopped tomatoes\n3 garlic cloves\n1 tsp chili flakes\nOlive oil\nParsley",
     "1. Sizzle garlic and chili in olive oil.\n2. Add tomatoes and simmer 15 minutes.\n3. Cook the penne al dente.\n4. Toss with the sauce and finish with parsley."),
    (14, "Smoked Fish Kedgeree", ["Breakfast"], "kedgeree",
     "300g smoked haddock\n250g basmati rice\n3 eggs\n1 onion\n1 tbsp curry powder\nButter, parsley, lemon",
     "1. Poach the haddock and soft-boil the eggs.\n2. Fry onion in butter with curry powder; stir in the rice.\n3. Fold in flaked fish and quartered eggs.\n4. Finish with parsley and lemon."),
]



STOP_WORDS = {
    "and", "the", "for", "with", "cup", "cups", "tbsp", "tsp", "pinch",
    "large", "small", "medium", "fresh", "chopped", "sliced", "diced",
    "ground", "handful", "juice", "zest", "optional", "plus", "serve",
    "taste", "into", "some", "ripe", "whole", "half",
}

def extract_ingredient_keywords(ingredients):
    """Same logic as the app: searchable words from the ingredients text."""
    import re as _re
    words = _re.findall(r"[a-z]+", ingredients.lower())
    return {w: True for w in words if len(w) >= 3 and w not in STOP_WORDS}

def request(method, url, data=None, headers=None):
    headers = headers or {}
    headers.setdefault(
        "User-Agent",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
    )
    if isinstance(data, (dict, list)):
        data = json.dumps(data).encode()
        headers.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30, context=SSL_CTX) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()


def main():
    if not os.path.exists(GS_PATH):
        sys.exit("google-services.json not found at app/ — download it from the Firebase console first.")

    gs = json.load(open(GS_PATH))
    api_key = gs["client"][0]["api_key"][0]["current_key"]
    project = gs["project_info"]["project_id"]
    bucket = gs["project_info"]["storage_bucket"]

    # --- discover the Realtime Database URL (region-dependent) ---
    db_url = None
    print("Probing database locations:")
    for candidate in (
        f"https://{project}-default-rtdb.firebaseio.com",
        f"https://{project}-default-rtdb.europe-west1.firebasedatabase.app",
        f"https://{project}-default-rtdb.asia-southeast1.firebasedatabase.app",
    ):
        status, body = request("GET", candidate + "/.json?shallow=true")
        print(f"  [{status}] {candidate}  {body.decode(errors='ignore')[:80]}")
        if status in (200, 401):
            db_url = candidate
            break
    if not db_url:
        sys.exit(
            "\nCould not find the Realtime Database.\n"
            "In the Firebase console: Databases and storage > Realtime Database.\n"
            "If you see a 'Create database' button — it was never created. Create it\n"
            "(any location, Start in test mode), then run this script again."
        )
    print(f"Database: {db_url}")

    # --- 1. create users ---
    print("\n== Creating users ==")
    accounts = []  # (uid, idToken, name, email)
    for name, email, bio, avatar_id in USERS:
        status, body = request(
            "POST",
            f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={api_key}",
            {"email": email, "password": PASSWORD, "returnSecureToken": True},
        )
        data = json.loads(body or b"{}")
        if status == 200:
            print(f"  created  {email}")
        elif "EMAIL_EXISTS" in body.decode(errors="ignore"):
            status, body = request(
                "POST",
                f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}",
                {"email": email, "password": PASSWORD, "returnSecureToken": True},
            )
            if status != 200:
                sys.exit(f"  {email}: exists but sign-in failed: {body.decode()[:200]}")
            data = json.loads(body)
            print(f"  exists   {email} (signed in)")
        elif "OPERATION_NOT_ALLOWED" in body.decode(errors="ignore"):
            sys.exit("Email/Password sign-in is not enabled in the console (Authentication > Sign-in method).")
        else:
            sys.exit(f"  {email}: failed ({status}): {body.decode()[:200]}")
        accounts.append((data["localId"], data["idToken"], name, email, bio, avatar_id))

    # --- 2. write user profiles ---
    print("\n== Writing profiles ==")
    photos = {}  # uid -> photoUrl, reused for demo comments
    recipe_counts = {}
    for r in RECIPES:
        recipe_counts[r[0]] = recipe_counts.get(r[0], 0) + 1
    for u_idx, (uid, token, name, email, bio, avatar_id) in enumerate(accounts):
        photo_url = ""
        # Primary: pravatar (real-looking photos). Fallback: ui-avatars
        # (colored initials) so every user always gets some picture.
        s, img = request("GET", f"https://i.pravatar.cc/300?img={avatar_id}")
        if s != 200 or not img:
            print(f"    pravatar failed [{s}], trying ui-avatars...")
            initials = urllib.parse.quote(name)
            s, img = request(
                "GET",
                f"https://ui-avatars.com/api/?name={initials}&size=256&background=F97350&color=fff&format=png&bold=true",
            )
            if s != 200 or not img:
                print(f"    ui-avatars failed [{s}] too")
        if s == 200 and img:
            object_name = urllib.parse.quote(f"profile_images/{uid}.jpg", safe="")
            s2, body2 = request(
                "POST",
                f"https://firebasestorage.googleapis.com/v0/b/{bucket}/o?uploadType=media&name={object_name}",
                img,
                {"Content-Type": "image/jpeg", "Authorization": f"Firebase {token}"},
            )
            if s2 == 200:
                dl = json.loads(body2).get("downloadTokens", "")
                photo_url = (
                    f"https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{object_name}"
                    f"?alt=media&token={dl}"
                )
        photos[uid] = photo_url
        status, body = request(
            "PUT",
            f"{db_url}/users/{uid}.json?auth={token}",
            {"uid": uid, "name": name, "nameLower": name.lower(), "email": email,
             "bio": bio, "photoUrl": photo_url, "recipeCount": recipe_counts.get(u_idx, 0)},
        )
        if status != 200:
            sys.exit(f"  profile for {email} failed ({status}): {body.decode()[:200]}")
        print(f"  {name:<18} avatar={'yes' if photo_url else 'no'}")
    print(f"  {len(accounts)} profiles written")

    # --- 3. recipes with photos ---
    print("\n== Uploading recipes ==")
    now = int(time.time() * 1000)
    image_cache = {}

    def fetch_food_image(term):
        """Look the dish up on TheMealDB (free API) and download its photo."""
        if term in image_cache:
            return image_cache[term]
        # "drink:" prefix -> TheCocktailDB; otherwise TheMealDB
        if term.startswith("drink:"):
            q = urllib.parse.quote(term[len("drink:"):])
            url = f"https://www.thecocktaildb.com/api/json/v1/1/search.php?s={q}"
            list_key, thumb_key = "drinks", "strDrinkThumb"
        else:
            q = urllib.parse.quote(term)
            url = f"https://www.themealdb.com/api/json/v1/1/search.php?s={q}"
            list_key, thumb_key = "meals", "strMealThumb"
        status, body = request("GET", url)
        img = None
        if status == 200:
            try:
                items = json.loads(body).get(list_key) or []
                if items:
                    s, data = request("GET", items[0][thumb_key])
                    if s == 200:
                        img = data
            except Exception:
                pass
        image_cache[term] = img
        return img

    for i, (owner, title, categories, term, ingredients, steps) in enumerate(RECIPES):
        uid, token, name = accounts[owner][:3]
        rid = f"seed-{i + 1:02d}"

        image_url = ""
        img = fetch_food_image(term)
        if img:
            object_name = urllib.parse.quote(f"recipe_images/{rid}.jpg", safe="")
            status, body = request(
                "POST",
                f"https://firebasestorage.googleapis.com/v0/b/{bucket}/o?uploadType=media&name={object_name}",
                img,
                {"Content-Type": "image/jpeg", "Authorization": f"Firebase {token}"},
            )
            if status == 200:
                dl_token = json.loads(body).get("downloadTokens", "")
                image_url = (
                    f"https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{object_name}"
                    f"?alt=media&token={dl_token}"
                )

        # ratings from 3 other demo users so the feed looks alive
        ratings = {}
        for j in range(1, 4):
            rater_uid = accounts[(owner + j) % len(accounts)][0]
            ratings[rater_uid] = 3 + (i + j) % 3  # 3..5 stars

        recipe = {
            "id": rid,
            "title": title,
            "titleLower": title.lower(),
            "ingredientKeywords": extract_ingredient_keywords(ingredients),
            "ownerUid": uid,
            "ownerName": name,
            "categories": categories,
            "ingredients": ingredients,
            "steps": steps,
            "imageUrl": image_url,
            "createdAt": now - i * 3_600_000,  # one hour apart
            "ratings": ratings,
            "ratingSum": sum(ratings.values()),
            "ratingCount": len(ratings),
            "ratingAvg": round(sum(ratings.values()) / len(ratings), 4) if ratings else 0.0,
        }
        status, body = request("PUT", f"{db_url}/recipes/{rid}.json?auth={token}", recipe)
        if status != 200:
            sys.exit(f"  {title} failed ({status}): {body.decode()[:200]}")
        print(f"  {rid}  {title:<22} by {name:<16} photo={'yes' if image_url else 'no'}")

    # --- 4. demo comments: questions, Chef replies, and follow-ups on the reply ---
    print("\n== Writing comments ==")
    # recipe index (1-based) -> thread of (who, text); who = "owner" or an offset
    COMMENT_THREADS = {
        1: [(3, "Can I use canned tomatoes instead of fresh?"),
            ("owner", "Absolutely — one can of good quality crushed tomatoes works great."),
            (3, "Tried it tonight with canned — came out perfect, thanks chef!")],
        3: [(2, "How spicy is this? Cooking for kids."),
            ("owner", "Very mild as written. Skip the chili flakes and it's fully kid-friendly.")],
        5: [(4, "Made this twice this week already. Dangerous recipe.")],
        9: [(3, "Mine came out dry, any tips?"),
            ("owner", "Pull them while the center still jiggles — they keep cooking in the tin."),
            (5, "The jiggle tip saved my batch too. Game changer.")],
        12: [(1, "Perfect taco night, the seasoning balance is spot on.")],
        13: [(2, "My dough keeps tearing when I stretch it, help!"),
             ("owner", "Let it rest 15 more minutes at room temp — cold dough always fights back."),
             (2, "Rested it like you said — stretched like a dream. Toda!")],
        17: [(4, "Is tahini a must?"),
             ("owner", "It's the heart of the dish, but in a pinch use more olive oil and lemon.")],
        21: [(2, "Best lasagne I've made, the creme fraiche trick is genius."),
             ("owner", "So happy it worked for you! It's my nonna-approved shortcut.")],
        23: [(5, "What rice can I use if I can't find paella rice?"),
             ("owner", "Arborio is the closest — just add the stock a bit more gradually."),
             (7, "Confirming arborio works, made it yesterday by the beach!")],
        25: [(6, "That molten center photo convinced me. Making it tonight.")],
        28: [(1, "Finally a properly balanced mojito, not too sweet."),
             ("owner", "Bartender's honor — the trick is dissolving the sugar before the ice.")],
        30: [(8, "Can I make this with tofu instead of chicken?"),
             ("owner", "Definitely — press it well and add it in the last 10 minutes."),
             (8, "Tofu version was excellent. New weeknight staple!")],
    }
    now2 = int(time.time() * 1000)
    token_by_uid = {acc[0]: acc[1] for acc in accounts}
    for idx, thread in COMMENT_THREADS.items():
        rid = f"seed-{idx:02d}"
        owner_idx = RECIPES[idx - 1][0]

        # Security rules only let an author edit/delete their own comments,
        # so clear stale comments from previous runs using each author's token.
        s, body = request(
            "GET", f"{db_url}/comments/{rid}.json?auth={accounts[owner_idx][1]}"
        )
        if s == 200 and body and body != b"null":
            try:
                existing = json.loads(body) or {}
            except Exception:
                existing = {}
            for cid, data in existing.items():
                tok = token_by_uid.get((data or {}).get("authorUid", ""))
                if tok:
                    request("DELETE", f"{db_url}/comments/{rid}/{cid}.json?auth={tok}")

        base_ts = now2 - len(thread) * 3_600_000
        for i, (who, text) in enumerate(thread, 1):
            if who == "owner":
                acc = accounts[owner_idx]
            else:
                acc = accounts[(owner_idx + who) % len(accounts)]
            uid, token, name = acc[0], acc[1], acc[2]
            # Demo likes: chef replies get 3 likes, follow-ups 1, questions
            # get a like from the chef. Never let an author like themselves.
            if who == "owner":
                like_offsets = (1, 4, 6)
            elif i == 1:
                like_offsets = (0,)  # the chef liked the question
            else:
                like_offsets = (5,)
            likes = {}
            for off in like_offsets:
                liker_uid = accounts[(owner_idx + off) % len(accounts)][0]
                if liker_uid != uid:
                    likes[liker_uid] = True
            comment = {
                "id": f"c{i}",
                "recipeId": rid,
                "authorUid": uid,
                "authorName": name,
                "authorPhotoUrl": photos.get(uid, ""),
                "text": text,
                "createdAt": base_ts + i * 3_600_000,
                "edited": False,
                "likes": likes,
            }
            s, b = request("PUT", f"{db_url}/comments/{rid}/c{i}.json?auth={token}", comment)
            if s != 200:
                sys.exit(f"  comment on {rid} failed ({s}): {b.decode()[:200]}")
        print(f"  {rid}: {len(thread)} comments")

    # --- 5. credentials file ---
    creds_path = os.path.join(HERE, "seed_users_credentials.txt")
    with open(creds_path, "w") as f:
        f.write("MyEats demo users (password for all: %s)\n\n" % PASSWORD)
        for acc in accounts:
            name, email = acc[2], acc[3]
            f.write(f"{name:<20} {email}\n")
    print(f"\nDone! Credentials saved to {creds_path}")


if __name__ == "__main__":
    main()

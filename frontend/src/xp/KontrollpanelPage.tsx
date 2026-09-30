import { useEffect, useRef, useState } from "react";
import { Button } from "./Button";
import { KontrollAppsPage } from "./KontrollAppsPage";
import { KontrollOrgPage } from "./KontrollOrgPage";
import { KontrollTemaPage } from "./KontrollTemaPage";
import { XpIcon, type IconName } from "./XpIcon";
import { copy } from "./copy";

type Category = "apps" | "org" | "tema";

const CATEGORIES: ReadonlyArray<{
  id: Category;
  icon: IconName;
  title: string;
  sub: string;
}> = [
  {
    id: "apps",
    icon: "app",
    title: copy["s9.cat.apps"],
    sub: copy["s9.cat.apps.sub"],
  },
  {
    id: "org",
    icon: "bruker",
    title: copy["s9.cat.org"],
    sub: copy["s9.cat.org.sub"],
  },
  {
    id: "tema",
    icon: "kontrollpanel",
    title: copy["s9.cat.tema"],
    sub: copy["s9.cat.tema.sub"],
  },
];

function CategoryPage({ id }: { id: Category }) {
  switch (id) {
    case "apps":
      return <KontrollAppsPage />;
    case "org":
      return <KontrollOrgPage />;
    case "tema":
      return <KontrollTemaPage />;
  }
}

export function KontrollpanelPage() {
  const [category, setCategory] = useState<Category | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const navigated = useRef(false);

  useEffect(() => {
    if (navigated.current) headingRef.current?.focus();
    navigated.current = true;
  }, [category]);

  const selected = CATEGORIES.find((c) => c.id === category);

  if (!selected) {
    return (
      <>
        <div className="xp-pagehead">
          <XpIcon name="kontrollpanel" size={32} />
          <h1 ref={headingRef} tabIndex={-1} className="xp-h1">
            {copy["s9.title"]}
          </h1>
        </div>
        <p>{copy["s9.note"]}</p>
        {CATEGORIES.map((c) => (
          <button
            key={c.id}
            type="button"
            className="xp-card xp-menuitem"
            onClick={() => setCategory(c.id)}
          >
            <XpIcon name={c.icon} size={32} />
            <span>
              <strong>{c.title}</strong>
              <span className="sub">{c.sub}</span>
            </span>
          </button>
        ))}
      </>
    );
  }

  return (
    <>
      <div className="xp-pagehead">
        <Button variant="link" onClick={() => setCategory(null)}>
          {copy["s9.back"]}
        </Button>
      </div>
      <div className="xp-pagehead">
        <XpIcon name={selected.icon} size={32} />
        <h1 ref={headingRef} tabIndex={-1} className="xp-h1">
          {selected.title}
        </h1>
      </div>
      <CategoryPage id={selected.id} />
    </>
  );
}

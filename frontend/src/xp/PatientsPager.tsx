import { Button } from "./Button";
import { copy } from "./copy";

type Props = {
  from: number;
  to: number;
  total: number;
  unfilteredTotal: number;
  page: number;
  pages: number;
  onPage: (page: number) => void;
};

export function PatientsPager({
  from,
  to,
  total,
  unfilteredTotal,
  page,
  pages,
  onPage,
}: Props) {
  return (
    <div className="xp-pager">
      <span role="status">
        {copy["s3.pager.showing"](from, to, total)}
        {unfilteredTotal !== total && ` ${copy["s3.pager.filtered"](unfilteredTotal)}`}
      </span>
      <span className="grow" />
      <span>{copy["s3.pager.page"](page, pages)}</span>
      <Button
        aria-label={copy["s3.pager.prev.aria"]}
        disabled={page <= 1}
        onClick={() => onPage(page - 1)}
      >
        {copy["s3.pager.prev"]}
      </Button>
      <Button
        aria-label={copy["s3.pager.next.aria"]}
        disabled={page >= pages}
        onClick={() => onPage(page + 1)}
      >
        {copy["s3.pager.next"]}
      </Button>
    </div>
  );
}

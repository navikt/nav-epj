import * as React from "react";
import { Outlet, createRootRoute } from "@tanstack/react-router";
import { HStack, InternalHeader, Search, Spacer } from "@navikt/ds-react";

export const Route = createRootRoute({
  component: RootComponent,
});

function RootComponent() {
  return (
    <React.Fragment>
      <InternalHeader>
        <InternalHeader.Title>Dr. Zara</InternalHeader.Title>
        <Spacer />
        <HStack
          as="form"
          paddingInline="space-20"
          align="center"
          onSubmit={(e) => {
            e.preventDefault();
            console.info("Search!");
          }}
        >
          <Search
            label="InternalHeader søk"
            size="small"
            variant="simple"
            placeholder="Søk"
          />
        </HStack>
        <InternalHeader.User name="Zev" />
      </InternalHeader>
      <main>
        <Outlet />
      </main>
    </React.Fragment>
  );
}

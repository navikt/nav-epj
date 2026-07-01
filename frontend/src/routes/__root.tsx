import * as React from "react";
import { Outlet, createRootRoute } from "@tanstack/react-router";
import { HStack, InternalHeader, Search, Spacer } from "@navikt/ds-react";
import { useEffect, useState } from "react";
import type { Helsepersonell } from "@utils/mapping/epj";

export const Route = createRootRoute({
  component: RootComponent,
});




function RootComponent() {
   const [isLoading, setIsLoading] = useState(true);
   const [userInfo, setUserInfo] = useState<Helsepersonell | null>(null)
  
    useEffect(() => {
      async function fetchHelsepersonell() {
        const info = await fetch('/api/helsepersonell/me').then((res) => res.json())
        setUserInfo(info);
        setIsLoading(false)
      }
      // TODO: Opprette helsepersonell
      fetchHelsepersonell()
      
    
    }, [])
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
        <InternalHeader.User name={userInfo?.navn} />
      </InternalHeader>
      <main>
        {isLoading ? <div>Laster...</div> : 
        <Outlet />
        }
      </main>
    </React.Fragment>
  );
}
